package norswap.sigh;

//import jdk.nashorn.internal.objects.Global;
//import com.sun.org.apache.xpath.internal.operations.Bool;
import norswap.sigh.ast.*;
import norswap.sigh.interpreter.Constructor;
import norswap.sigh.scopes.DeclarationContext;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Attribute;
import norswap.uranium.Reactor;
import norswap.uranium.Rule;
import norswap.uranium.SemanticError;
import norswap.utils.visitors.ReflectiveFieldWalker;
import norswap.utils.visitors.Walker;
import org.w3c.dom.Attr;
/*import org.graalvm.compiler.lir.LIRInstruction.Temp;
import org.w3c.dom.Attr;
import javax.management.openmbean.SimpleType;*/
import java.lang.reflect.Array;
import java.sql.Ref;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static norswap.sigh.ast.BinaryOperator.*;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.forEachIndexed;
import static norswap.utils.Vanilla.list;
import static norswap.utils.visitors.WalkVisitType.POST_VISIT;
import static norswap.utils.visitors.WalkVisitType.PRE_VISIT;

/**
 * Holds the logic implementing semantic analyzis for the language, including typing and name
 * resolution.
 *
 * <p>The entry point into this class is {@link #createWalker(Reactor)}.
 *
 * <h2>Big Principles
 * <ul>
 *     <li>Every {@link DeclarationNode} instance must have its {@code type} attribute to an
 *     instance of {@link Type} which is the type of the value declared (note that for struct
 *     declaration, this is always {@link TypeType}). Template type was introduced to be able
 *     to give an abstract type to parameters with template type</li>
 *
 *     <li>Additionally, {@link StructDeclarationNode} (and default
 *     {@link SyntheticDeclarationNode} for types) must have their {@code declared} attribute set to
 *     an instance of the type being declared.</li>
 *
 *     <li>Every {@link ExpressionNode} instance must have its {@code type} attribute similarly
 *     set.</li>
 *
 *     <li>Every {@link ReferenceNode} instance must have its {@code decl} attribute set to the the
 *     declaration it references and its {@code scope} attribute set to the {@link Scope} in which
 *     the declaration it references lives. This speeds up lookups in the interpreter and simplifies the compiler.</li>
 *
 *     <li>For the same reasons, {@link VarDeclarationNode} and {@link ParameterNode} should have
 *     their {@code scope} attribute set to the scope in which they appear (this also speeds up the
 *     interpreter).</li>
 *
 *     <li>All statements introducing a new scope must have their {@code scope} attribute set to the
 *     corresponding {@link Scope} (only {@link RootNode}, {@link BlockNode} and {@link
 *     FunDeclarationNode} (for parameters)). These nodes must also update the {@code scope}
 *     field to track the current scope during the walk.</li>
 *
 *     <li>Every {@link TypeNode} instance must have its {@code value} set to the {@link Type} it
 *     denotes.</li>
 *
 *     <li>Every {@link ReturnNode}, {@link BlockNode} and {@link IfNode} must have its {@code
 *     returns} attribute set to a boolean to indicate whether its execution causes
 *     unconditional exit from the surrounding function or main script.</li>
 *
 *     <li>Every {@link TemplateParameterNode} contains the name of the parameters given in the template statement (template<typename T>)</li>
 *
 *     <li>The rules check typing constraints: assignment of values to variables, of arguments to
 *     parameters, checking that if/while conditions are booleans, and array indices are
 *     integers.</li>
 *
 *     <li>The rules also check a number of other constraints: that accessed struct fields exist,
 *     that variables are declared before being used, etc...</li>
 * </ul>
 */
public final class SemanticAnalysis
{
    // =============================================================================================
    // region [Initialization]
    // =============================================================================================

    private final Reactor R;

    /** Current scope. */
    private Scope scope;

    /** Current context for type inference (currently only to infer the type of empty arrays). */
    private SighNode inferenceContext;

    /** Index of the current function argument. */
    private int argumentIndex;


    /**template C++
     * globalTypeDictionary for function
     * = hashmap with key = function name, value = hashmap with key = paramater, value = real type*/
    private HashMap<String, List<HashMap<String, Type>>> globalTypeDictionary = new HashMap<>();

    /** variableToTemplate for function
     * = hashmap with key = function name, value = hashmap with key = parameter, value = type templateParameter */
    private HashMap<String, HashMap<String, String>> variableToTemplate = new HashMap<>();

    /** structDeclarationMap for function
     * = hashmap with key */
    private HashMap<String, String> structDeclarationMap = new HashMap<>();

    /** Return type
     * */
    private HashMap<String, List<Type>> returnTemplateDic = new HashMap<>();
    private HashMap<String, Integer> returnCounterFunction = new HashMap<>();

    /**Template[]
     *  = set with authorized operations between strings **/
    private HashSet<BinaryOperator> string_op = new HashSet<>();
    private HashSet<BinaryOperator> bool_op = new HashSet<>();
    HashMap<String,List<HashMap<String,ArrayLiteralNode>>> param_arrays = new HashMap<>();

    /**Avoid mutiple declarations with same name**/
    private HashSet<String> declaredNames = new HashSet<>();
    private HashMap<String, HashSet<String>> declaredVarNames = new HashMap<>();

    // ---------------------------------------------------------------------------------------------

    private SemanticAnalysis(Reactor reactor) {
        this.R = reactor;
        BinaryOperator[] sop ={ADD,GREATER,GREATER_EQUAL,LOWER,LOWER_EQUAL,EQUALITY,NOT_EQUALS};
        this.string_op.addAll(Arrays.asList(sop));
        BinaryOperator[] bop ={AND,OR,EQUALITY,NOT_EQUALS};
        this.bool_op.addAll(Arrays.asList(bop));
        declaredVarNames.put("global", new HashSet<>());

    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Call this method to create a tree walker that will instantiate the typing rules defined
     * in this class when used on an AST, using the given {@code reactor}.
     */
    public static Walker<SighNode> createWalker (Reactor reactor)
    {
        ReflectiveFieldWalker<SighNode> walker = new ReflectiveFieldWalker<>(
            SighNode.class, PRE_VISIT, POST_VISIT);

        SemanticAnalysis analysis = new SemanticAnalysis(reactor);

        // expressions
        walker.register(IntLiteralNode.class,           PRE_VISIT,  analysis::intLiteral);
        walker.register(FloatLiteralNode.class,         PRE_VISIT,  analysis::floatLiteral);
        walker.register(StringLiteralNode.class,        PRE_VISIT,  analysis::stringLiteral);
        walker.register(ReferenceNode.class,            PRE_VISIT,  analysis::reference);
        walker.register(ConstructorNode.class,          PRE_VISIT,  analysis::constructor);
        walker.register(ArrayLiteralNode.class,         PRE_VISIT,  analysis::arrayLiteral);
        walker.register(ParenthesizedNode.class,        PRE_VISIT,  analysis::parenthesized);
        walker.register(FieldAccessNode.class,          PRE_VISIT,  analysis::fieldAccess);
        walker.register(ArrayAccessNode.class,          PRE_VISIT,  analysis::arrayAccess);
        walker.register(FunCallNode.class,              PRE_VISIT,  analysis::funCall);
        walker.register(UnaryExpressionNode.class,      PRE_VISIT,  analysis::unaryExpression);
        walker.register(BinaryExpressionNode.class,     PRE_VISIT,  analysis::binaryExpression);
        walker.register(AssignmentNode.class,           PRE_VISIT,  analysis::assignment);
        //C++ templates
        walker.register(TemplateTypeNode.class, PRE_VISIT, analysis::templateType);
        // types
        walker.register(SimpleTypeNode.class,           PRE_VISIT,  analysis::simpleType);
        walker.register(ArrayTypeNode.class,            PRE_VISIT,  analysis::arrayType);


        // declarations & scopes
        walker.register(RootNode.class,                 PRE_VISIT,  analysis::root);
        walker.register(BlockNode.class,                PRE_VISIT,  analysis::block);
        walker.register(VarDeclarationNode.class,       PRE_VISIT,  analysis::varDecl);
        walker.register(FieldDeclarationNode.class,     PRE_VISIT,  analysis::fieldDecl);
        walker.register(ParameterNode.class,            PRE_VISIT,  analysis::parameter);
        //C++ templates
        walker.register(TemplateParameterNode.class, PRE_VISIT, analysis::templateParameter);
        walker.register(FunDeclarationNode.class,       PRE_VISIT,  analysis::funDecl);
        walker.register(StructDeclarationNode.class,    PRE_VISIT,  analysis::structDecl);

        walker.register(RootNode.class,                 POST_VISIT, analysis::popScope);
        walker.register(BlockNode.class,                POST_VISIT, analysis::popScope);
        walker.register(FunDeclarationNode.class,       POST_VISIT, analysis::popScope);

        // statements
        walker.register(ExpressionStatementNode.class,  PRE_VISIT,  node -> {});
        walker.register(IfNode.class,                   PRE_VISIT,  analysis::ifStmt);
        walker.register(WhileNode.class,                PRE_VISIT,  analysis::whileStmt);
        walker.register(ReturnNode.class,               PRE_VISIT,  analysis::returnStmt);

        walker.registerFallback(POST_VISIT, node -> {});

        return walker;
    }

    // endregion
    // =============================================================================================
    // region [Expressions]
    // =============================================================================================

    private void intLiteral (IntLiteralNode node) {
        R.set(node, "type", IntType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void floatLiteral (FloatLiteralNode node) {
        R.set(node, "type", FloatType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void stringLiteral (StringLiteralNode node) {
        R.set(node, "type", StringType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------
    //C++ templates
    private void templateType (TemplateTypeNode node) {
        R.set(node, "type", TemplateType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void reference (ReferenceNode node)
    {
        final Scope scope = this.scope;





        // Try to lookup immediately. This must succeed for variables, but not necessarily for
        // functions or types. By looking up now, we can report looked up variables later
        // as being used before being defined.
        DeclarationContext maybeCtx = scope.lookup(node.name);

        if (maybeCtx != null) {
            R.set(node, "decl",  maybeCtx.declaration);
            R.set(node, "scope", maybeCtx.scope);

            R.rule(node, "type")
                .using(maybeCtx.declaration, "type")
                .by(Rule::copyFirst);
            return;
        }

        // Re-lookup after the scopes have been built.
        R.rule(node.attr("decl"), node.attr("scope"))
            .by(r -> {
                DeclarationContext ctx = scope.lookup(node.name);
                DeclarationNode decl = ctx == null ? null : ctx.declaration;
                if (ctx == null) {
                    r.errorFor("Could not resolve: " + node.name,
                        node, node.attr("decl"), node.attr("scope"), node.attr("type"));
                }
                else {
                    r.set(node, "scope", ctx.scope);
                    r.set(node, "decl", decl);

                    if (decl instanceof VarDeclarationNode)
                        r.errorFor("Variable used before declaration: " + node.name,
                            node, node.attr("type"));
                    else
                        R.rule(node, "type")
                            .using(decl, "type")
                            .by(Rule::copyFirst);
                }

            });
    }

    // ---------------------------------------------------------------------------------------------

    private void constructor (ConstructorNode node)
    {
        R.rule()
            .using(node.ref, "decl")
            .by(r -> {
                DeclarationNode decl = r.get(0);

                if (!(decl instanceof StructDeclarationNode)) {
                    String description =
                        "Applying the constructor operator ($) to non-struct reference for: "
                            + decl;
                    r.errorFor(description, node, node.attr("type"));
                    return;
                }

                StructDeclarationNode structDecl = (StructDeclarationNode) decl;

                Attribute[] dependencies = new Attribute[structDecl.fields.size() + 1];
                dependencies[0] = decl.attr("declared");
                forEachIndexed(structDecl.fields, (i, field) ->
                    dependencies[i + 1] = field.attr("type"));

                R.rule(node, "type")
                    .using(dependencies)
                    .by(rr -> {
                        Type structType = rr.get(0);
                        Type[] params = IntStream.range(1, dependencies.length).<Type>mapToObj(rr::get)
                            .toArray(Type[]::new);
                        rr.set(0, new FunType(structType, params));
                    });
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayLiteral (ArrayLiteralNode node)
    {
        if (node.components.size() == 0) { // []
            // Empty array: we need a type int to know the desired type.

            final SighNode context = this.inferenceContext;

            if (context instanceof VarDeclarationNode)
                R.rule(node, "type")
                    .using(context, "type")
                    .by(Rule::copyFirst);
            else if (context instanceof FunCallNode) {
                R.rule(node, "type")
                    .using(((FunCallNode) context).function.attr("type"), node.attr("index"))
                    .by(r -> {
                        FunType funType = r.get(0);
                        r.set(0, funType.paramTypes[(int) r.get(1)]);
                    });
            }
            return;
        }
        boolean templateArray = true;
        if (this.inferenceContext instanceof VarDeclarationNode){
            TypeNode t =((VarDeclarationNode)this.inferenceContext).type;
            if (t instanceof  ArrayTypeNode)
                templateArray =!(((ArrayTypeNode) t).contents().equals("Template[]"));
        }
        else {
            //TODO check if different inference context
            //System.out.println(((FunCallNode)this.inferenceContext).function);
        }

        boolean notTemplateArray = templateArray;
        Attribute[] dependencies =
            node.components.stream().map(it -> it.attr("type")).toArray(Attribute[]::new);


        R.rule(node, "type")
            .using(dependencies)
            .by(r -> {
                Type[] types = IntStream.range(0, dependencies.length).<Type>mapToObj(r::get)
                    .distinct().toArray(Type[]::new);

                int i = 0;
                Type supertype = null;
                for (Type type: types) {
                    if (type instanceof VoidType)
                        // We report the error, but compute a type for the array from the other elements.
                        r.errorFor("Void-valued expression in array literal", node.components.get(i));
                    else if (supertype == null)
                        supertype = type;
                    else {
                        supertype = commonSupertype(supertype, type);
                        if (supertype == null && notTemplateArray) {
                            r.error("Could not find common supertype in array literal.", node);
                            return;
                        }
                    }
                    ++i;
                }

                if (supertype == null  && notTemplateArray)
                    r.error(
                        "Could not find common supertype in array literal: all members have Void type.",
                        node);
                else{
                    r.set(0, new ArrayType(supertype, null));

                }

            });
    }

    // ---------------------------------------------------------------------------------------------

    private void parenthesized (ParenthesizedNode node)
    {
        R.rule(node, "type")
            .using(node.expression, "type")
            .by(Rule::copyFirst);
        R.set(node,"scope",scope);
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldAccess (FieldAccessNode node)
    {
        R.rule()
            .using(node.stem, "type")
            .by(r -> {
                Type type = r.get(0);

                if (type instanceof ArrayType) {
                    if (node.fieldName.equals("length"))
                        R.rule(node, "type")
                            .by(rr -> rr.set(0, IntType.INSTANCE));
                    else
                        r.errorFor("Trying to access a non-length field on an array", node,
                            node.attr("type"));
                    return;
                }

                if (!(type instanceof StructType)) {
                    r.errorFor("Trying to access a field on an expression of type " + type,
                        node,
                        node.attr("type"));
                    return;
                }

                StructDeclarationNode decl = ((StructType) type).node;

                for (DeclarationNode field: decl.fields)
                {
                    if (!field.name().equals(node.fieldName)) continue;

                    R.rule(node, "type")
                        .using(field, "type")
                        .by(Rule::copyFirst);

                    return;
                }

                String description = format("Trying to access missing field %s on struct %s",
                    node.fieldName, decl.name);
                r.errorFor(description, node, node.attr("type"));
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayAccess (ArrayAccessNode node)
    {
        R.rule()
            .using(node.index, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof IntType))
                    r.error("Indexing an array using a non-Int-valued expression", node.index);
            });
        final FunDeclarationNode scopeFunc = currentFunction();


        R.rule(node, "type")
            .using(node.array, "type")
            .by(r -> {
                Type type = null;
                List<Type> typeList = null;
                if (r.get(0) instanceof ArrayList)
                    typeList = r.get(0);
                else
                    type = r.get(0);

                String templateFromVarLeft = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.array.contents()) : null;
                if (scopeFunc != null && (templateFromVarLeft != null || typeList != null)) {
                    List<Type> typeToSet = new ArrayList<>();
                    for (int i = 0; i < globalTypeDictionary.get(scopeFunc.name).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(scopeFunc.name).get(i);
                        type = typeList != null ? typeList.get(i) : type;
                        type = templateFromVarLeft == null ? type : localHashmap.get(templateFromVarLeft);

                        if (type instanceof ArrayType){
                            typeToSet.add(((ArrayType) type).componentType);
                        }
                    }

                    r.set(0, typeToSet);
                }
                else if (type instanceof ArrayType){
                    r.set(0, ((ArrayType) type).componentType);
                }
                else
                    r.error("Trying to index a non-array expression of type " + type, node);
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void funCall (FunCallNode node)
    {
        //Template arrays
        String fun_name = node.function.contents();
        if (fun_name.equals("print")){
            if(inferenceContext instanceof FunCallNode){
                String inside_fname = ((FunCallNode) inferenceContext).function.contents();
                Scope s = new Scope(node,scope);
                s.declare(inside_fname, scope.declarations.get(inside_fname));
                R.set(node, "scope",scope);
            }

        }else {
            R.set(node, "scope", scope);
        }



        FunDeclarationNode fun_decl = null;
        if(scope.declarations.get(fun_name) instanceof FunDeclarationNode) {
            fun_decl = (FunDeclarationNode) scope.declarations.get(fun_name);
        }
        //TODO
        if (fun_decl != null){
            for (ParameterNode p : fun_decl.parameters){
                R.set(p,"scope",scope);
            }
        }


        // end Template arrays

        this.inferenceContext = node;
        Attribute[] dependencies;
        dependencies = new Attribute[node.arguments.size() + 1];
        dependencies[0] = node.function.attr("type");
        forEachIndexed(node.arguments, (i, arg) -> {
            dependencies[i + 1] = arg.attr("type");
            R.set(arg, "index", i);
        });
        DeclarationNode tempCurrFun = null;
        if (scope.declarations.get(node.function.contents()) instanceof FunDeclarationNode){
            tempCurrFun = ((FunDeclarationNode) scope.declarations.get(node.function.contents()));
        }else if (scope.declarations.get(node.function.contents().substring(1, node.function.contents().length())) instanceof StructDeclarationNode){
            tempCurrFun = scope.declarations.get(node.function.contents().substring(1, node.function.contents().length()));
        }

        final DeclarationNode toCheckFun = tempCurrFun;
        StructDeclarationNode currStruct = null;
        FunDeclarationNode currFun = null;
        if (node.function instanceof ConstructorNode){
            currStruct = (StructDeclarationNode) toCheckFun;
        }
        else{
            currFun = (FunDeclarationNode) toCheckFun;
        }

        /*if (currFun.templateParameters == null && node.templateArgs != null){
            R.error("Try to give types as argument but no template parameter was declared", node, node);
        } -> TODO add it to prevent funCall<Int> without template has been specified*/
        HashMap<String, Type> templateParametersDictionnary = new HashMap<>();
        if ((toCheckFun != null && node.templateArgs != null)) {


            Boolean checkfun = currFun != null && node.templateArgs.size() != 0 && currFun.getTemplateParameters() != null && node.templateArgs.size() == currFun.getTemplateParameters().size();
            Boolean checkstruct = currStruct != null && node.templateArgs.size() != 0 && currStruct.getTemplateParameters() != null && node.templateArgs.size() == currStruct.getTemplateParameters().size();

            if (checkfun || checkstruct) {
                int tempIdx = 0;
                int templateNameIdx = 0;
                List<TemplateParameterNode> toIter;
                if (checkfun)
                    toIter = currFun.getTemplateParameters();
                else
                    toIter = currStruct.getTemplateParameters();
                for (TemplateParameterNode templateName : toIter) {
                    Type template;

                    switch (node.templateArgs.get(tempIdx).contents()){
                        case "Int":
                            template = IntType.INSTANCE;
                            templateNameIdx++;
                            break;
                        case "Float":
                            template = FloatType.INSTANCE;
                            templateNameIdx++;
                            break;
                        case "String":
                            template = StringType.INSTANCE;
                            templateNameIdx++;
                            break;
                        case "Bool":
                            template = BoolType.INSTANCE;
                            templateNameIdx++;
                            break;
                        case "Template[]":
                            template = new ArrayType(TemplateType.INSTANCE,"Template[]");//TemplateType.INSTANCE;
                            templateNameIdx++;
                            break;
                        case "Template":
                            template = TemplateType.INSTANCE;
                            templateNameIdx++;
                            break;
                        /*case "Array":
                            TODO
                         */
                        default:
                            //r.errorFor(format("This type %s is not allowed for the Template function", actualType), node);
                            template = null;
                    }
                    if (currFun != null){
                        String returnName = currFun.returnType.contents();
                        Boolean d=  false;
                        if (returnName.contains("[]")){
                            returnName = returnName.substring(0, returnName.length()-2);
                            d=true;

                        }
                        if (returnName.equals("T") || returnName.charAt(0) == ('T') && Character.isDigit(returnName.charAt(1))){
                            if (returnTemplateDic.get(node.function.contents()) == null){
                                returnTemplateDic.put(node.function.contents(), new ArrayList<>());
                                returnCounterFunction.put(node.function.contents(), 0);
                            }
                            if (templateName.name.equals(returnName)){
                                if (d){
                                    returnTemplateDic.get(node.function.contents()).add(new ArrayType(TemplateType.INSTANCE,"Template[]"));

                                }
                            }

                        }
                    }
                    templateParametersDictionnary.put(templateName.name, template);
                    if (! (template instanceof ArrayType)){
                        templateParametersDictionnary.put(templateName.name, template);
                        templateParametersDictionnary.put(templateName.name+"[]", new ArrayType(template, null));
                    }else {// template[]
                        templateParametersDictionnary.put(templateName.name, TemplateType.INSTANCE);
                        templateParametersDictionnary.put(templateName.name+"[]", template);//new ArrayType(template, null));
                    }
                    tempIdx++;
                }
                if (globalTypeDictionary.get(node.function.contents()) == null){
                    List<HashMap<String, Type>> tempList = new ArrayList<>();
                    tempList.add(templateParametersDictionnary);
                    globalTypeDictionary.put(node.function.contents(), tempList);
                }else{
                    globalTypeDictionary.get(node.function.contents()).add(templateParametersDictionnary);
                }
            }
        }
        FunDeclarationNode finalCurrFun = currFun;
        StructDeclarationNode finalCurrStruct = currStruct;
        R.rule(node, "type")
            .using(dependencies)
            .by(r -> {
                Type maybeFunType = r.get(0);
                if (!(maybeFunType instanceof FunType)) {
                    r.error("trying to call a non-function expression: " + node.function.contents(), node.function);
                    return;
                }
                //function check
                if (finalCurrFun != null && finalCurrFun.getTemplateParameters() != null) {
                    if (node.templateArgs != null && node.templateArgs.size() != 0) {
                        if(node.templateArgs.size() != finalCurrFun.getTemplateParameters().size()){
                            r.error(format("Wrong number of template arguments in %s: expected %d but got %d", node.function.contents(), finalCurrFun.getTemplateParameters().size(), node.templateArgs.size()), node.function);
                            return;
                        }
                    }else{
                        r.error("Trying to call template function without giving any types in arg: " + node.function.contents(), node.function);
                        return;
                    }
                }else if(finalCurrFun != null && node.templateArgs != null  && node.templateArgs.size() != 0){
                    r.error("Trying to use template that were not declared: " + node.function.contents(), node.function);
                    return;
                }

                //struct check
                if (finalCurrStruct != null && finalCurrStruct.getTemplateParameters() != null) {
                    if (node.templateArgs != null && node.templateArgs.size() != 0) {
                        if(node.templateArgs.size() != finalCurrStruct.getTemplateParameters().size()){
                            r.error(format("Wrong number of template arguments in %s: expected %d but got %d", node.function.contents(), finalCurrStruct.getTemplateParameters().size(), node.templateArgs.size()), node.function);
                            return;
                        }
                    }else{
                        r.error("Trying to call template function without giving any types in arg: " + node.function.contents(), node.function);
                        return;
                    }
                }else if(finalCurrStruct != null && node.templateArgs != null  && node.templateArgs.size() != 0){
                    r.error("Trying to use template that were not declared: " + node.function.contents(), node.function);
                    return;
                }

                FunType funType = cast(maybeFunType);
                r.set(0, funType.returnType);
                Type[] params = funType.paramTypes;
                Type[] paramsToChange = funType.changingParamTypes;
                int idx = node.arguments.size()+1;
                int templateNameIdx = 0;
                for (int i = 0; i<params.length; i++){
                    if (params[i] instanceof TemplateType){
                        String paramName = ((TemplateType) params[i]).getParamName(node.function.contents(), templateNameIdx);
                        Type actualType = templateParametersDictionnary.get(paramName);
                        templateNameIdx++;
                        paramsToChange[i] = actualType;
                        idx++;
                    }
                    else if(params[i] instanceof ArrayType && templateParametersDictionnary.size() != 0){
                        ArrayType arrType = (ArrayType) params[i];
                        Type actualType = templateParametersDictionnary.get((arrType).templateName);
                        templateNameIdx++;
                        //TODO check if actualType was needed here
                        paramsToChange[i] = actualType==null? r.get(i+1): new ArrayType(actualType,null); //actualType;

                    }
                }
                List<ExpressionNode> args = node.arguments;
                List<TypeNode> templateArgs = node.templateArgs;
                if (params.length != args.size())
                    r.errorFor(format("wrong number of arguments, expected %d but got %d",
                        params.length, args.size()),
                        node);

                int checkedArgs = Math.min(params.length, args.size());
                for (int i = 0; i < checkedArgs; ++i) {
                    List<Type> argsType = null;
                    Type argType = null;
                    if(r.get(i+1) instanceof ArrayList)
                        argsType = r.get(i+1);
                    else
                        argType = r.get(i + 1);
                    Type paramType = paramsToChange[i];
                    if (argsType != null){
                        for (Type arg : argsType ){
                            if (!isAssignableTo(arg, paramType) && !(arg instanceof TemplateType))
                                r.errorFor(format(
                                    "incompatible argument provided for argument %d: expected %s but got %s",
                                    i, paramType, arg),
                                    node.arguments.get(i));
                        }
                    }else{
                        if (!isAssignableTo(argType, paramType) && !(argType instanceof TemplateType))
                            r.errorFor(format(
                                "incompatible argument provided for argument %d: expected %s but got %s",
                                i, paramType, argType),
                                node.arguments.get(i));
                    }
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void unaryExpression (UnaryExpressionNode node)
    {
        assert node.operator == UnaryOperator.NOT; // only one for now
        R.set(node, "type", BoolType.INSTANCE);

        R.rule()
            .using(node.operand, "type")
            .by(r -> {
                Type opType = r.get(0);
                if (!(opType instanceof BoolType) && !(opType instanceof TemplateType))
                    r.error("Trying to negate type: " + opType, node);
            });
    }

    // endregion
    // =============================================================================================
    // region [Binary Expressions]
    // =============================================================================================

    private void binaryExpression (BinaryExpressionNode node)
    {

        final FunDeclarationNode scopeFunc = currentFunction();
        R.set(node,"scope",scope);
        R.rule(node, "type")
            .using(node.left.attr("type"), node.right.attr("type"))
            .by(r -> {

                /*Type left  = r.get(0);
                Type right = r.get(1);*/
                Type left = null;
                Type right = null;
                List<Type> leftList = null;
                List<Type> rightList = null;
                if (r.get(0) instanceof ArrayList)
                    leftList = r.get(0);
                else
                    left = r.get(0);
                if (r.get(1) instanceof ArrayList)
                    rightList = r.get(1);
                else
                    right = r.get(1);
                String templateFromVarLeft = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.left.contents()) : null;
                String templateFromVarRight = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.right.contents()): null;
                templateFromVarLeft = scopeFunc == null && node.left instanceof FieldAccessNode && structDeclarationMap.containsKey(((FieldAccessNode) node.left).stem.contents()) ? variableToTemplate.get(structDeclarationMap.get(((FieldAccessNode) node.left).stem.contents())).get(((FieldAccessNode) node.left).fieldName): templateFromVarLeft;
                templateFromVarRight = scopeFunc == null && node.right instanceof FieldAccessNode && structDeclarationMap.containsKey(((FieldAccessNode) node.right).stem.contents()) ? variableToTemplate.get(structDeclarationMap.get(((FieldAccessNode) node.right).stem.contents())).get(((FieldAccessNode) node.right).fieldName): templateFromVarRight;
                if ((scopeFunc == null && !(node.left instanceof FieldAccessNode) && !(node.right instanceof FieldAccessNode)) || (templateFromVarLeft == null && templateFromVarRight == null && leftList == null && rightList == null)){
                    if (node.operator == ADD && (left instanceof StringType || right instanceof StringType))
                        r.set(0, StringType.INSTANCE);
                    else if (isArithmetic(node.operator)){
                        binaryArithmetic(r, node, left, right, null);
                    }
                    else if (isComparison(node.operator))
                        binaryComparison(r, node, left, right);
                    else if (isLogic(node.operator))
                        binaryLogic(r, node, left, right);
                    else if (isEquality(node.operator))
                        binaryEquality(r, node, left, right);
                    else if (isArrayOp(node.operator))
                        arrayArithmetic(r,node,left,right,node.array_operator, null);
                }else{
                    List<Type> typesToSet = new ArrayList<>();
                    if (globalTypeDictionary.size() == 0){
                        r.set(0, typesToSet);
                        return;
                    }

                    String toSearch;
                    if(scopeFunc != null)
                        toSearch = scopeFunc.name;
                    else{
                        if (node.left instanceof FieldAccessNode)
                            toSearch = structDeclarationMap.get(((FieldAccessNode) node.left).stem.contents());
                        else
                            toSearch = structDeclarationMap.get(((FieldAccessNode) node.right).stem.contents());
                    }
                    for (int i = 0; i < globalTypeDictionary.get(toSearch).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(toSearch).get(i);
                        left = leftList != null ? leftList.get(i): left;
                        right = rightList != null ? rightList.get(i): right;
                        left = templateFromVarLeft == null ? left : localHashmap.get(templateFromVarLeft);
                        right = templateFromVarRight == null ? right : localHashmap.get(templateFromVarRight);
                        if (node.operator == ADD && (left instanceof StringType || right instanceof StringType))
                            typesToSet.add(StringType.INSTANCE);
                        else if (isArithmetic(node.operator))
                            binaryArithmetic(r, node, left, right, typesToSet);
                        else if (isComparison(node.operator)) {
                            typesToSet.add(BoolType.INSTANCE);
                            binaryComparison(r, node, left, right);
                        }
                        else if (isLogic(node.operator)){
                            typesToSet.add(BoolType.INSTANCE);
                            binaryLogic(r, node, left, right);
                        }
                        else if (isEquality(node.operator)){
                            typesToSet.add(BoolType.INSTANCE);
                            binaryEquality(r, node, left, right);
                        }
                        else if (isArrayOp(node.operator)){
                            arrayArithmetic(r,node,left,right,node.array_operator, typesToSet);
                        }
                    }
                    r.set(0, typesToSet);
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isArithmetic (BinaryOperator op) {
        return op == ADD || op == MULTIPLY || op == SUBTRACT || op == DIVIDE || op == REMAINDER;
    }

    private boolean isComparison (BinaryOperator op) {
        return op == GREATER || op == GREATER_EQUAL || op == LOWER || op == LOWER_EQUAL;
    }

    private boolean isLogic (BinaryOperator op) {
        return op == OR || op == AND;
    }

    private boolean isEquality (BinaryOperator op) {
        return op == EQUALITY || op == NOT_EQUALS;
    }

    //Template[]

    private boolean isArrayOp (BinaryOperator op){
        return op == ARRAY_OP;
    }
    private boolean isBoolOp(BinaryOperator op){
        return op == AND || op == OR;
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryArithmetic (Rule r, BinaryExpressionNode node, Type left, Type right, List<Type> typeToSet)
    {
        if (typeToSet == null){
            if (left instanceof IntType)
                if (right instanceof IntType)
                    r.set(0, IntType.INSTANCE);
                else if (right instanceof FloatType)
                    r.set(0, FloatType.INSTANCE);
                else
                    r.error(arithmeticError(node, "Int", right), node);
            else if (left instanceof FloatType)
                if (right instanceof IntType || right instanceof FloatType)
                    r.set(0, FloatType.INSTANCE);
                else
                    r.error(arithmeticError(node, "Float", right), node);
            else{
                r.error(arithmeticError(node,left,right),node);
            }

        if (left instanceof IntType)
            if (right instanceof IntType)
                r.set(0, IntType.INSTANCE);
            else if (right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else
                r.error(arithmeticError(node, "Int", right), node);
        else if (left instanceof FloatType)
            if (right instanceof IntType || right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else
                r.error(arithmeticError(node, left, right), node);
        }else{
            if (left instanceof IntType)
                if (right instanceof IntType)
                    typeToSet.add(IntType.INSTANCE);
                else if (right instanceof FloatType)
                    typeToSet.add(FloatType.INSTANCE);
                else
                    r.errorFor(arithmeticError(node, "Int", right), node);
            else if (left instanceof FloatType)
                if (right instanceof IntType || right instanceof FloatType)
                    typeToSet.add(FloatType.INSTANCE);
                else
                    r.errorFor(arithmeticError(node, "Float", right), node);
            else{
                r.errorFor(arithmeticError(node, left, right), node);
            }
        }

    }

    // ---------------------------------------------------------------------------------------------

    private static String arithmeticError (BinaryExpressionNode node, Object left, Object right) {
        return format("Trying to %s %s with %s", node.operator.name().toLowerCase(), left, right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryComparison (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof IntType) && !(left instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + left,
                node.left);
        if (!(right instanceof IntType) && !(right instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + right,
                node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryEquality (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);
        if (!isComparableTo(left, right))
            r.errorFor(format("Trying to compare incomparable types %s and %s", left, right),
                node);

    }

    // ---------------------------------------------------------------------------------------------

    private void binaryLogic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + left,
                node.left);
        if (!(right instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + right,
                node.right);
    }
    private boolean check_arrays(Rule r,List left_array, List right_array, boolean temp, BinaryExpressionNode node,BinaryOperator op){
        //arrays of different sizes
        if (left_array.size() != right_array.size()){
            r.error(format("Trying to use @ between arrays of different lengths : %s and %s", left_array.size(),right_array.size()),node);
            return false;
        }
        // types check for template[]
        if (temp){
            for (int i=0; i < left_array.size(); i++){

                boolean left_numeric = (left_array.get(i) instanceof IntLiteralNode || left_array.get(i) instanceof FloatLiteralNode);
                boolean right_numeric = (right_array.get(i) instanceof IntLiteralNode || right_array.get(i) instanceof FloatLiteralNode);

                if ((left_numeric != right_numeric) && (left_array.get(i).getClass() != right_array.get(i).getClass())){
                    r.error("Trying to use @ between template arrays of different types",node);
                    return false;
                }
                if (!left_numeric && !(this.string_op.contains(op))){
                    r.error(format("Trying to use illegal operation %s between strings in template arrays",op),node);
                    return false;
                }
            }

        }
        return true;

    }

    private void set_array_type(Rule r, BinaryExpressionNode node, Type left, boolean temp, List<Type> typeToSet){
        ArrayType arrayType = (ArrayType) left;
        if (typeToSet==null){
            typeToSet = new ArrayList<>();
        }
        if (isComparison(node.array_operator) || isEquality(node.array_operator) || isBoolOp(node.array_operator)){
            if (temp){
                typeToSet.add(new ArrayType(BoolType.INSTANCE,"Template[]"));
                r.set(0,new ArrayType(BoolType.INSTANCE,"Template[]"));
            }else{
                typeToSet.add(new ArrayType(BoolType.INSTANCE,null));
                r.set(0,new ArrayType(BoolType.INSTANCE,null));
            }
        }
        else{
            if (temp){
                typeToSet.add(new ArrayType(TemplateType.INSTANCE,"Template[]"));
                r.set(0,new ArrayType(TemplateType.INSTANCE,"Template[]"));
            }else{
                typeToSet.add(new ArrayType(arrayType.componentType,null));
                r.set(0,new ArrayType(arrayType.componentType,null));
            }
        }
    }

    //Template[]
    private void arrayArithmetic (Rule r, BinaryExpressionNode node, Type left, Type right,BinaryOperator op, List<Type> typeToSet)
    {
        Scope curr_scope = R.get(inferenceContext,"scope");
        if (!(left instanceof ArrayType) || !(right instanceof ArrayType)){
            r.errorFor("Trying to use @ between non ArrayTypes", node);
            set_array_type(r,node,left,false, typeToSet);
            return;
        }
        String left_name = null;
        if (node.left instanceof ReferenceNode){

            left_name=((ReferenceNode) node.left).name;
        }
        String right_name =null;
        if (node.right instanceof ReferenceNode){
            right_name=((ReferenceNode) node.right).name;
        }
        Boolean left_template = (((ArrayType) left).templateName != null && ((ArrayType) left).templateName.equals("Template[]") || left.name().equals("Template[]") ) ;
        Boolean right_template = (((ArrayType) right).templateName != null && ((ArrayType) right).templateName.equals("Template[]") || right.name().equals("Template[]"));
        boolean temp = left_template || right_template;
        if ( !temp && ((ArrayType) left).componentType instanceof StringType && ((ArrayType) right).componentType instanceof StringType){
            if (! string_op.contains(node.array_operator)) {
                set_array_type(r,node,left,false, typeToSet);
                r.error(format("Trying to use %s between arrays of String type",node.array_operator),node);
                return;
            }
        }

        if ( !temp && ((ArrayType) left).componentType instanceof BoolType && ((ArrayType) right).componentType instanceof BoolType){
            if (! bool_op.contains(node.array_operator)) {
                set_array_type(r,node,left,false, typeToSet);
                r.error(format("Trying to use %s between arrays of Bool type",node.array_operator),node);
                return;
            }
        }

        if (!temp &&! left.name().equals(right.name())){
            if (left_name != null && right_name != null) {
                boolean left_num = left.name().equals("Int[]") || left.name().equals("Float[]");
                boolean right_num = right.name().equals("Int[]") || right.name().equals("Float[]");
                if (!left_num || !right_num) {
                    set_array_type(r,node,left,false, typeToSet);
                    r.error(format("Trying to use @ between non compatible ArrayTypes %s and %s", left.name(), right.name()), node);
                    return;
                }
            }else {
                boolean left_num = left.name().equals("Int[]") || left.name().equals("Float[]");
                boolean right_num = right.name().equals("Int[]") || right.name().equals("Float[]");
                if (!left_num || !right_num) {
                    set_array_type(r,node,left,false, typeToSet);
                    r.error(format("Trying to use @ between non compatible ArrayTypes %s and %s", left.name(), right.name()), node);
                    return;
                }

            }
        }
        set_array_type(r,node,left,temp, typeToSet);
    }

    // ---------------------------------------------------------------------------------------------

    private void assignment (AssignmentNode node)
    {
        final FunDeclarationNode scopeFunc = currentFunction();
        R.rule(node, "type")
            .using(node.left.attr("type"), node.right.attr("type"))
            .by(r -> {
                Type left  = r.get(0);
                Type right = null;
                List<Type> rightList = null;
                if (r.get(1) instanceof ArrayList)
                    rightList = r.get(1);
                else
                    right = r.get(1);
                String templateFromVarLeft = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.left.contents()): null;
                String templateFromVarRight = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.right.contents()): null;
                templateFromVarLeft = scopeFunc == null && node.left instanceof FieldAccessNode && structDeclarationMap.containsKey(((FieldAccessNode) node.left).stem.contents()) ? variableToTemplate.get(structDeclarationMap.get(((FieldAccessNode) node.left).stem.contents())).get(((FieldAccessNode) node.left).fieldName): templateFromVarLeft;
                templateFromVarRight = scopeFunc == null && node.right instanceof FieldAccessNode && structDeclarationMap.containsKey(((FieldAccessNode) node.right).stem.contents()) ? variableToTemplate.get(structDeclarationMap.get(((FieldAccessNode) node.right).stem.contents())).get(((FieldAccessNode) node.right).fieldName): templateFromVarRight;
                if (templateFromVarRight != null || templateFromVarLeft != null|| rightList != null){
                    if (globalTypeDictionary.size() == 0){
                        r.set(0, left);
                        return;
                    }
                    String toSearch;
                    if(scopeFunc != null)
                        toSearch = scopeFunc.name;
                    else{
                        if (node.left instanceof FieldAccessNode)
                            toSearch = structDeclarationMap.get(((FieldAccessNode) node.left).stem.contents());
                        else
                            toSearch = structDeclarationMap.get(((FieldAccessNode) node.right).stem.contents());
                    }
                    for (int i = 0; i < globalTypeDictionary.get(toSearch).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(toSearch).get(i);
                        right = rightList != null ? rightList.get(i): right;
                        left = templateFromVarLeft == null ? left : localHashmap.get(templateFromVarLeft);
                        right = templateFromVarRight == null ? right : localHashmap.get(templateFromVarRight);
                        r.set(0, left); // the type of the assignment is the left-side type
                        if (node.left instanceof ReferenceNode
                            ||  node.left instanceof FieldAccessNode
                            ||  node.left instanceof ArrayAccessNode) {
                            if (!isAssignableTo(right, left)){
                                r.errorFor(format("Trying to assign a value of type %s to a non-compatible lvalue of type %s.", right, left), node);
                            }
                        }
                        else
                            r.errorFor("Trying to assign to an non-lvalue expression.", node.left);
                    }
                }else{
                    r.set(0, r.get(0)); // the type of the assignment is the left-side type

                    if (node.left instanceof ReferenceNode
                        ||  node.left instanceof FieldAccessNode
                        ||  node.left instanceof ArrayAccessNode) {
                        if (!isAssignableTo(right, left) && !left.name().equals("Template")){
                            r.errorFor(format("Trying to assign a value of type %s to a non-compatible lvalue of type %s.", right, left), node);
                        }
                    }
                    else
                        r.errorFor("Trying to assign to an non-lvalue expression.", node.left);
                }

            });
    }

    // endregion
    // =============================================================================================
    // region [Types & Typing Utilities]
    // =============================================================================================

    private void simpleType (SimpleTypeNode node)
    {
        final Scope scope = this.scope;

        R.rule()
            .by(r -> {
                // type declarations may occur after use
                DeclarationContext ctx = scope.lookup(node.name);
                DeclarationNode decl = ctx == null ? null : ctx.declaration;

                if (ctx == null)
                    r.errorFor("could not resolve: " + node.name,
                        node,
                        node.attr("value"));

                else if (!isTypeDecl(decl))
                    r.errorFor(format(
                        "%s did not resolve to a type declaration but to a %s declaration",
                        node.name, decl.declaredThing()),
                        node,
                        node.attr("value"));

                else
                    R.rule(node, "value")
                        .using(decl, "declared")
                        .by(Rule::copyFirst);
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayType (ArrayTypeNode node)
    {
        R.rule(node, "value")
            .using(node.componentType, "value")
            .by(r -> r.set(0, new ArrayType(r.get(0), node.templateName)));
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean isTypeDecl (DeclarationNode decl)
    {
        if (decl instanceof StructDeclarationNode)
            return true;
        if (!(decl instanceof SyntheticDeclarationNode)){
            return false;
        }

        SyntheticDeclarationNode synthetic = cast(decl);
        return synthetic.kind() == DeclarationKind.TYPE;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicates whether a value of type {@code a} can be assigned to a location (variable,
     * parameter, ...) of type {@code b}.
     */
    private static boolean isAssignableTo (Type a, Type b)
    {

        if ((a instanceof ArrayType && (!(b instanceof ArrayType))) || (b instanceof ArrayType && !(a instanceof ArrayType)))
            return false;
        if (a instanceof ArrayType ){
            if (((ArrayType) a).templateName != null && b.name().equals("Template[]")){ // Template array

                return true;
            }
            return b instanceof ArrayType
                && isAssignableTo(((ArrayType)a).componentType, ((ArrayType)b).componentType);
        }
        if (a != null && b != null && a.toString().contains("[]") && b.toString().contains("[]")){
            switch (a.toString().substring(0, a.toString().length() -2)){
                case "Int":
                    a = IntType.INSTANCE;
                case "Float":
                    a = FloatType.INSTANCE;
                case "String":
                    a = StringType.INSTANCE;
                case "Bool":
                    a = BoolType.INSTANCE;
            }
            switch (b.toString().substring(0, b.toString().length() -2)){
                case "Int":
                    b = IntType.INSTANCE;
                case "Float":
                    b = FloatType.INSTANCE;
                case "String":
                    b = StringType.INSTANCE;
                case "Bool":
                    b = BoolType.INSTANCE;
            }
        }
        if (a==null ||b==null)
            return false;
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        if (a instanceof IntType && b instanceof FloatType)
            return true;

        if (b instanceof TemplateType){
            return true;
        }
        if (b instanceof ArrayType && ((ArrayType) b).templateName != null &&((ArrayType)b).templateName.equals("Template[]")){
            if (a instanceof TemplateType)
                return true;
            else
                return false;
        }
        if (a instanceof TemplateType){
            //System.out.println("WARNING : trying to attribute type to Template array element");
            return true;
        }
        return a instanceof NullType && b.isReference() || a.equals(b);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicate whether the two types are comparable.
     */
    private static boolean isComparableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        return a.isReference() && b.isReference()
            || a.equals(b)
            || a instanceof IntType && b instanceof FloatType
            || a instanceof FloatType && b instanceof IntType;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the common supertype between both types, or {@code null} if no such supertype
     * exists.
     */
    private static Type commonSupertype (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return null;
        if (isAssignableTo(a, b))
            return b;
        if (isAssignableTo(b, a))
            return a;
        else
            return null;
    }

    // endregion
    // =============================================================================================
    // region [Scopes & Declarations]
    // =============================================================================================

    private void popScope (SighNode node) {
        scope = scope.parent;
    }

    // ---------------------------------------------------------------------------------------------

    private void root (RootNode node) {
        assert scope == null;
        scope = new RootScope(node, R);
        R.set(node, "scope", scope);
    }

    // ---------------------------------------------------------------------------------------------

    private void block (BlockNode node) {
        scope = new Scope(node, scope);
        R.set(node, "scope", scope);

        Attribute[] deps = getReturnsDependencies(node.statements);
        R.rule(node, "returns")
            .using(deps)
            .by(r -> r.set(0, deps.length != 0 && Arrays.stream(deps).anyMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void varDecl (VarDeclarationNode node)
    {

        this.inferenceContext = node;
        final FunDeclarationNode scopeFunc = currentFunction();
        String paramTypeName = node.type.contents();
        if (paramTypeName.contains("[]"))
            paramTypeName = paramTypeName.substring(0, paramTypeName.length()-2);
        if ((paramTypeName.equals("T") || (paramTypeName.charAt(0) == ('T') && Character.isDigit(paramTypeName.charAt(1)))) && scopeFunc != null) {
            if(variableToTemplate.containsKey(scopeFunc.name)){
                HashMap<String, String> temp = variableToTemplate.get(scopeFunc.name);
                temp.put(node.name, node.type.contents());
            }
            else{
                HashMap<String, String> temp = new HashMap<>();
                temp.put(node.name, node.type.contents());
                variableToTemplate.put(scopeFunc.name, temp);
            }
        }// TODO when var is declared with template in fun add it to variable template to be able to recognize it -> done but not checked !
        if (node.initializer instanceof FunCallNode && ((FunCallNode) node.initializer).templateArgs != null){
            structDeclarationMap.put(node.name, ((FunCallNode) node.initializer).function.contents());
        }
        scope.declare(node.name, node);
        R.set(node, "scope", scope);
        R.rule(node, "type")
            .using(node.type, "value")
            .by(Rule::copyFirst);
        Scope s = scope;
        if(scopeFunc == null)
            if (declaredVarNames.get("global").contains(node.name())){
                R.error(new SemanticError("Try to declare variable with already existing name: " + node.name(),null,node));
            }else {
                HashSet<String> tempSet = declaredVarNames.get("global");
                tempSet.add(node.name());
            }
        else{
            if (declaredVarNames.get(scopeFunc.name) == null){
                declaredVarNames.put(scopeFunc.name(), new HashSet<>());
            }
            if (declaredVarNames.get(scopeFunc.name).contains(node.name())){
                R.error(new SemanticError("Try to declare variable with already existing name: " + node.name(),null,node));
            }else {
                HashSet<String> tempSet = declaredVarNames.get(scopeFunc.name);
                tempSet.add(node.name());
            }
        }
        R.rule()
            .using(node.type.attr("value"), node.initializer.attr("type"))
            .by(r -> {
                Type expected = r.get(0);
                Type actual = null;
                List<Type> actualList = null;
                if (r.get(1) instanceof ArrayList)
                    actualList = r.get(1);
                else
                    actual = r.get(1);
                if (node.type instanceof ArrayTypeNode && node.initializer == null)
                    throw new NullPointerException();
                String templateFromVarLeft = expected instanceof TemplateType? node.type.contents(): null;
                String templateFromVarRight = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.initializer.contents()): null;
                templateFromVarRight = scopeFunc == null && node.initializer instanceof FieldAccessNode && structDeclarationMap.containsKey(((FieldAccessNode) node.initializer).stem.contents()) ? variableToTemplate.get(structDeclarationMap.get(((FieldAccessNode) node.initializer).stem.contents())).get(((FieldAccessNode) node.initializer).fieldName): templateFromVarRight;
                String funName = scopeFunc != null ? scopeFunc.name: null;
                if(node.initializer instanceof FunCallNode && ((FunCallNode) node.initializer).templateArgs != null && !node.initializer.contents().contains("$")){
                    templateFromVarRight = "FunCall";
                    funName = ((FunCallNode) node.initializer).function.contents();
                }
                if (templateFromVarRight != null || templateFromVarLeft != null || actualList != null){
                    if (globalTypeDictionary.size() == 0)
                        return;
                    if(node.initializer instanceof FunCallNode && !node.initializer.contents().contains("$")){
                        //TODO OK ou pas ?
                        if (returnCounterFunction.size()==0){
                            return;
                        }
                        int iter = returnCounterFunction.get(funName);
                        actual = returnTemplateDic.get(funName).get(iter);
                        expected = templateFromVarLeft == null || expected.name().equals("Template") ? expected : globalTypeDictionary.get(funName).get(iter).get(templateFromVarLeft);
                        returnCounterFunction.put(funName, iter+1);
                        if (!isAssignableTo(actual, expected)) {
                            r.error(format(
                                    "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                    node.name, expected, actual),
                                node.initializer);
                        }
                    }else{
                        String toSearch;
                        if(scopeFunc != null)
                            toSearch = scopeFunc.name;
                        else{
                            if (structDeclarationMap.containsKey(node.name))
                                toSearch = structDeclarationMap.get(node.name);
                            else
                                toSearch = structDeclarationMap.get(node.initializer.contents().split("[.]")[0]);

                        }
                        if (toSearch == null){
                            if (actualList != null){
                                for (int i = 0; i < actualList.size(); i++) {
                                    actual = actualList.get(i);
                                    if (!isAssignableTo(actual, expected)) {
                                        r.error(format(
                                            "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                            node.name, expected, actual),
                                            node.initializer);
                                    }
                                }
                            }

                        }else{
                            for (int i = 0; i < globalTypeDictionary.get(toSearch).size(); i++) {
                                HashMap<String, Type> localHashmap = globalTypeDictionary.get(toSearch).get(i);
                                actual = (actualList != null && actualList.size()!=0)? actualList.get(i): actual;
                                expected = templateFromVarLeft == null || expected.name().equals("Template") ? expected : localHashmap.get(templateFromVarLeft);
                                expected = (templateFromVarLeft == null||(templateFromVarLeft!=null && templateFromVarLeft.equals("Template"))  )? expected : localHashmap.get(templateFromVarLeft);
                                actual = templateFromVarRight == null ? actual : localHashmap.get(templateFromVarRight);

                                if (!isAssignableTo(actual, expected)) {
                                    r.error(format(
                                            "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                            node.name, expected, actual),
                                        node.initializer);
                                }
                            }
                        }
                    }
                }
                else if (! (expected.name().equals("Template[]"))){
                    if (actual instanceof ArrayType){
                        if (((ArrayType) actual).templateName == null){
                            if (!isAssignableTo(actual, expected)) {
                                r.error(format(
                                        "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                        node.name, expected, actual),
                                    node.initializer);
                            }
                        }
                        else { //right hand is Template array

                            //TODO assign template to normal array ???
                            if (! isAssignableTo(actual,expected)){
                                r.error(format(
                                        "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                        node.name, expected, actual),
                                    node.initializer);
                            }


                        }

                    }else {
                        if (!isAssignableTo(actual, expected)) {
                            r.error(format(
                                    "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                    node.name, expected, actual),
                                node.initializer);
                        }
                    }

                }

            });
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldDecl (FieldDeclarationNode node)
    {
        R.rule(node, "type")
            .using(node.type, "value")
            .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void parameter (ParameterNode node)
    {
        R.set(node, "scope", scope);
        scope.declare(node.name, node); // scope pushed by FunDeclarationNode
        R.rule(node, "type")
            .using(node.type, "value")
            .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------
    //C++ templates
    private void templateParameter (TemplateParameterNode node)
    {
        R.set(node, "scope", scope);
        scope.declare(node.name, node); // scope pushed by FunDeclarationNode
        R.rule(node, "type").using(node.type, "type")
            .by(Rule::copyFirst);
    }
    // ---------------------------------------------------------------------------------------------

    private void funDecl (FunDeclarationNode node)
    {

        scope.declare(node.name, node);
        scope = new Scope(node, scope);

        R.set(node, "scope", scope);
        if (declaredNames.contains(node.name())){
            R.error(new SemanticError("Try to declare function with already existing name: "  + node.name(),null,node));
            return;
        }else {
            declaredNames.add(node.name());
        }
        variableToTemplate.put(node.name, new HashMap<>());
        HashMap<String, String> tempVariableToTemplate = variableToTemplate.get(node.name);
        for (ParameterNode param : node.parameters){
            String type = param.type.contents();
            boolean d = false;
            if (type.contains("[]")){
                type = type.substring(0, type.length()-2);
                d = true;
            }
            if (type.equals("T") || type.charAt(0) == ('T') && Character.isDigit(type.charAt(1))){
                if (d){
                    tempVariableToTemplate.put(param.name, type+"[]");

                }else
                    tempVariableToTemplate.put(param.name, type);

            }
        }
        Attribute[] dependencies;
        dependencies = new Attribute[node.parameters.size() + 1];
        dependencies[0] = node.returnType.attr("value");
        forEachIndexed(node.parameters, (i, param) ->
            dependencies[i + 1] = param.attr("type"));

        R.rule(node, "type")
            .using(dependencies)
            .by (r -> {
                if (node.templateParameters != null){
                    for (TemplateParameterNode templateParam : node.templateParameters){
                        String[] l = {"Int", "Float", "String", "Bool","Int[]", "Float[]", "String[]", "Bool[]", "Template", "Template[]"};
                        List<String> allowedTypes = new ArrayList<>(Arrays.asList(l));
                        String paramTypeName = templateParam.name;
                        if (!(paramTypeName.equals("T") || paramTypeName.charAt(0) == ('T') && Character.isDigit(paramTypeName.charAt(1))) && !allowedTypes.contains(paramTypeName)){
                            r.error(paramTypeName + " is not an allowed name for template", node);
                            return;
                        }
                    }
                }
                for (ParameterNode param : node.parameters) {
                    String paramTypeName = param.type.contents();
                    if (paramTypeName.contains("[")){
                        paramTypeName = paramTypeName.substring(0, paramTypeName.length()-2);
                    }
                    if (paramTypeName.equals("T") || paramTypeName.charAt(0) == ('T') && Character.isDigit(paramTypeName.charAt(1))) {
                        if (node.templateParameters == null) {

                            r.error("No template declaration was made", node);
                            return;
                        } else if (!node.templateParameters.contains(new TemplateParameterNode(node.span, paramTypeName, new TemplateTypeNode(node.span, paramTypeName)))) {
                            r.error("Wrong template declaration " + paramTypeName + " was not found", node);
                            return;
                        }
                        TemplateType.INSTANCE.pushParamName(node.name, paramTypeName);
                    }
                }
                Type[] paramTypes;
                paramTypes = new Type[node.parameters.size()];
                for (int i = 0; i < paramTypes.length; ++i){
                    paramTypes[i] = r.get(i + 1);
                }
                r.set(0, new FunType(r.get(0), paramTypes));
            });
        R.rule()
            .using(node.block.attr("returns"), node.returnType.attr("value"))
            .by(r -> {
                boolean returns = r.get(0);
                Type returnType = r.get(1);
                if (!returns && !(returnType instanceof VoidType))
                    r.error("Missing return in function.", node);
                // NOTE: The returned value presence & type is checked in returnStmt().
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void structDecl (StructDeclarationNode node) {
        scope.declare(node.name, node);
        R.set(node, "type", TypeType.INSTANCE);
        R.set(node, "declared", new StructType(node));
        if (declaredNames.contains(node.name)){
            R.error(new SemanticError("Try to declare struct with already existing name: "  + node.name(),null,node));
            return;
        }else{
            declaredNames.add(node.name);
        }
        if (node.templateParameters != null){
            List<String> templateParamNames = new ArrayList<>();
            for (TemplateParameterNode t : node.templateParameters)
                templateParamNames.add(t.name);
            for (FieldDeclarationNode toCheck : node.fields){
                String type = toCheck.type.contents();
                if ((type.equals("T") || type.charAt(0) == ('T') && Character.isDigit(type.charAt(1))) && !templateParamNames.contains(type)){
                    R.error(new SemanticError("Trying to use a non declared template parameters in the type of field " + toCheck.name + " in " + node.name, null, node));
                }
            }
        }else{
            for (FieldDeclarationNode toCheck : node.fields){
                String type = toCheck.type.contents();
                if ((type.equals("T") || type.charAt(0) == ('T') && Character.isDigit(type.charAt(1)))){
                    R.error(new SemanticError("Trying to use template type in field while struct not declared as template in " + node.name, null, node));
                }
            }
        }

        if (node.templateParameters != null){
            globalTypeDictionary.put("$"+node.name, new ArrayList<>());
            HashMap<String, String> tempHashmap = new HashMap<>();
            for (FieldDeclarationNode field : node.fields){
                TemplateType.INSTANCE.pushParamName("$"+node.name, field.type.contents());
                tempHashmap.put(field.name, field.type.contents());
            }
            variableToTemplate.put("$"+node.name, tempHashmap);
        }
    }

    // endregion
    // =============================================================================================
    // region [Other Statements]
    // =============================================================================================

    private void ifStmt (IfNode node) {
        FunDeclarationNode scopeFunc = currentFunction();
        R.rule()
            .using(node.condition, "type")
            .by(r -> {
                Type type = null;
                List<Type> typeList = null;
                if (r.get(0) instanceof ArrayList)
                    typeList = r.get(0);
                else
                    type = r.get(0);
                String templateFromVar = scopeFunc != null && node.condition.contents().length() == 3 ? variableToTemplate.get(scopeFunc.name).get(node.condition.contents().substring(1,2)): null;
                String[] potentialStruct = null;
                if (node.condition.contents().length() > 2){
                    potentialStruct = node.condition.contents().substring(1, node.condition.contents().length()-1).split("[.]");
                    if((potentialStruct != null && potentialStruct.length > 1))
                        templateFromVar = scopeFunc == null && structDeclarationMap.containsKey(potentialStruct[0]) && variableToTemplate.get(structDeclarationMap.get(potentialStruct[0])).containsKey(potentialStruct[1]) ? variableToTemplate.get(structDeclarationMap.get(potentialStruct[0])).get(potentialStruct[1]): templateFromVar;
                }
                if (templateFromVar != null || typeList != null){
                    if (globalTypeDictionary.size() == 0)
                        return;
                    String toSearch;
                    if(scopeFunc != null)
                        toSearch = scopeFunc.name;
                    else if(variableToTemplate.get(structDeclarationMap.get(potentialStruct[0])).containsKey(potentialStruct[1])){
                        toSearch = structDeclarationMap.get(potentialStruct[0]);
                    }else{
                        for (Type cond : typeList){
                            if (!(cond instanceof BoolType)) {
                                r.error("If statement with a non-boolean condition of type: " + cond,
                                    node.condition);
                            }
                        }
                        return;
                    }
                    for (int i = 0; i < globalTypeDictionary.get(toSearch).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(toSearch).get(i);
                        type = typeList != null? typeList.get(i): type;
                        type = templateFromVar == null? type: localHashmap.get(templateFromVar);
                        if (!(type instanceof BoolType)) {
                            r.error("If statement with a non-boolean condition of type: " + type,
                                node.condition);
                        }
                    }
                }
                else if (!(type instanceof BoolType)) {
                    r.error("If statement with a non-boolean condition of type: " + type,
                        node.condition);
                }
            });

        Attribute[] deps = getReturnsDependencies(list(node.trueStatement, node.falseStatement));
        R.rule(node, "returns")
            .using(deps)
            .by(r -> r.set(0, deps.length == 2 && Arrays.stream(deps).allMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void whileStmt (WhileNode node) {
        FunDeclarationNode scopeFunc = currentFunction();
        R.rule()
            .using(node.condition, "type")
            .by(r -> {
                Type type = null;
                List<Type> typeList = null;
                if (r.get(0) instanceof ArrayList)
                    typeList = r.get(0);
                else
                    type = r.get(0);
                String templateFromVar = scopeFunc != null && node.condition.contents().length() == 3 ? variableToTemplate.get(scopeFunc.name).get(node.condition.contents().substring(1,2)): null;
                String[] potentialStruct = null;
                if(node.condition.contents().length()> 2){
                    potentialStruct = node.condition.contents().substring(1, node.condition.contents().length()-1).split("[.]");
                    if (potentialStruct != null && potentialStruct.length > 1)
                        templateFromVar = scopeFunc == null && structDeclarationMap.containsKey(potentialStruct[0]) && variableToTemplate.get(structDeclarationMap.get(potentialStruct[0])).containsKey(potentialStruct[1]) ? variableToTemplate.get(structDeclarationMap.get(potentialStruct[0])).get(potentialStruct[1]): templateFromVar;
                }
                if (templateFromVar != null || typeList != null){
                    if(globalTypeDictionary.size() == 0)
                        return;
                    String toSearch;
                    if(scopeFunc != null)
                        toSearch = scopeFunc.name;
                    else if(variableToTemplate.get(structDeclarationMap.get(potentialStruct[0])).containsKey(potentialStruct[1])){
                        toSearch = structDeclarationMap.get(potentialStruct[0]);
                    }else{
                        for (Type cond : typeList){
                            if (!(cond instanceof BoolType)) {
                                r.error("If statement with a non-boolean condition of type: " + cond,
                                    node.condition);
                            }
                        }
                        return;
                    }
                    for (int i = 0; i < globalTypeDictionary.get(toSearch).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(toSearch).get(i);
                        type = typeList != null? typeList.get(i): type;
                        type = templateFromVar == null ? type : localHashmap.get(templateFromVar);
                        if (!(type instanceof BoolType)) {
                            r.error("While statement with a non-boolean condition of type: " + type,
                                node.condition);
                        }
                    }
                }
                else if (!(type instanceof BoolType)) {
                    r.error("While statement with a non-boolean condition of type: " + type,
                        node.condition);
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void returnStmt (ReturnNode node)
    {
        R.set(node, "returns", true);

        FunDeclarationNode scopeFunc = currentFunction();
        if (scopeFunc == null) // top-level return
            return;

        if (node.expression == null)
            R.rule()
                .using(scopeFunc.returnType, "value")
                .by(r -> {
                    Type returnType = r.get(0);
                    if (!(returnType instanceof VoidType))
                        r.error("Return without value in a function with a return type.", node);
                });
        else
            R.rule()
                .using(scopeFunc.returnType.attr("value"), node.expression.attr("type"))
                .by(r -> {
                    Type formal = r.get(0);
                    Type actual = null;
                    List<Type> actualList = null;
                    if (r.get(1) instanceof ArrayList)
                        actualList = r.get(1);
                    else
                        actual = r.get(1);
                    String name = scopeFunc.returnType.contents();
                    String subname = name;
                    if (name.contains("[]"))
                        subname = name.substring(0, name.length()-2);
                    String templateFromVarLeft = null;
                    if (subname.equals("T") || subname.charAt(0) == ('T') && Character.isDigit(subname.charAt(1)))
                        templateFromVarLeft = name;
                    String templateFromVarRight = variableToTemplate.get(scopeFunc.name).get(node.expression.contents());
                    if (formal instanceof VoidType)
                        r.error("Return with value in a Void function.", node);
                    else if (templateFromVarRight != null || templateFromVarLeft != null || actualList != null){
                        if(globalTypeDictionary.size() == 0 || (actualList != null && actualList.size() == 0))
                            return;
                        for (int i = 0; i < globalTypeDictionary.get(scopeFunc.name).size(); i++) {
                            HashMap<String, Type> localHashmap = globalTypeDictionary.get(scopeFunc.name).get(i);
                            actual = (actualList != null && actualList.size()!=0)? actualList.get(i): actual;
                            formal = templateFromVarLeft == null ? formal : localHashmap.get(templateFromVarLeft);
                            actual = templateFromVarRight == null ? actual : localHashmap.get(templateFromVarRight);
                            if (!isAssignableTo(actual, formal))
                                r.error(format(
                                    "Incompatible return type, expected %s but got %s", formal, actual),
                                    node.expression);
                        }
                    }
                    else if (!isAssignableTo(actual, formal)) {
                        r.errorFor(format(
                            "Incompatible return type , expected %s but got %s", formal, actual),
                            node.expression);
                    }
                });
    }

    // ---------------------------------------------------------------------------------------------

    private FunDeclarationNode currentFunction()
    {
        Scope scope = this.scope;
        while (scope != null) {
            SighNode node = scope.node;
            if (node instanceof FunDeclarationNode)
                return (FunDeclarationNode) node;
            scope = scope.parent;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isReturnContainer (SighNode node) {
        return node instanceof BlockNode
            || node instanceof IfNode
            || node instanceof ReturnNode;
    }

    // ---------------------------------------------------------------------------------------------

    /** Get the depedencies necessary to compute the "returns" attribute of the parent. */
    private Attribute[] getReturnsDependencies (List<? extends SighNode> children) {
        return children.stream()
            .filter(Objects::nonNull)
            .filter(this::isReturnContainer)
            .map(it -> it.attr("returns"))
            .toArray(Attribute[]::new);
    }

    // endregion
    // =============================================================================================
}