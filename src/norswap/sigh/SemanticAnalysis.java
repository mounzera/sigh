package norswap.sigh;

//import jdk.nashorn.internal.objects.Global;
import norswap.sigh.ast.*;
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


    /**Test for template C++
     * globalTypeDictionary = hashmap with key = function name, value = hashmap with key = paramater, value = real type*/
    private HashMap<String, List<HashMap<String, Type>>> globalTypeDictionary = new HashMap<>();

    /** variableToTemplate
     * = hashmap with key = function name, value = hashmap with key = parameter, value = type templateParameter */
    private HashMap<String, HashMap<String, String>> variableToTemplate = new HashMap<>();

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
    private HashSet<String> declaredFunNames = new HashSet<>();
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

        /*if (!node.name.equals("print") && (!node.name.equals("true"))){
            System.out.println("ref "+ node.name + " "+ ( (VarDeclarationNode) scope.declarations.get(node.name)).initializer);
        }*/


        //System.out.println(scope.declarations.get());


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
        //String name = ((VarDeclarationNode)this.inferenceContext).name;
        //array_components.put(name,node.components);
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
        //R.set(fun_decl.parameters.get(0),"scope",scope);
        //fun_decl.parameters.get(0).id++;//.array= (ArrayLiteralNode) node.arguments.get(0);
        /*ArrayList<String> param_names = new ArrayList<>();
        if (fun_decl !=null){
            for (ParameterNode param_name : fun_decl.parameters){
                param_names.add(param_name.name);
            }
        }

        List<ExpressionNode> current_params = node.arguments;
        HashMap<String,ArrayLiteralNode> local_param_arrays=null;
        int param_index=0;
        for (ExpressionNode possible_array : current_params){
            if (possible_array instanceof ArrayLiteralNode){
                if (local_param_arrays == null){
                    local_param_arrays = new HashMap<>();
                }
                local_param_arrays.put(param_names.get(param_index),(ArrayLiteralNode) possible_array);
            }else if (possible_array instanceof ReferenceNode){
                String param_name = ((ReferenceNode) possible_array).name;
                VarDeclarationNode array_decl = null;
                if(scope.declarations.get(param_name) instanceof  VarDeclarationNode){
                    array_decl =(VarDeclarationNode) (scope.declarations.get(param_name));
                }
                if (array_decl != null){
                    if (array_decl.initializer instanceof ArrayLiteralNode){
                        if (local_param_arrays == null){
                            local_param_arrays = new HashMap<>();
                        }
                        local_param_arrays.put(param_names.get(param_index),(ArrayLiteralNode) array_decl.initializer);
                    }
                }
            }
            param_index++;
        }
        if (local_param_arrays!=null ){
            if (!param_arrays.containsKey(fun_name)){
                param_arrays.put(fun_name, new ArrayList<>());
            }
            param_arrays.get(fun_name).add(local_param_arrays);
        }*/
        // end Template arrays

        this.inferenceContext = node;
        Attribute[] dependencies;
        dependencies = new Attribute[node.arguments.size() + 1];
        dependencies[0] = node.function.attr("type");
        forEachIndexed(node.arguments, (i, arg) -> {
            dependencies[i + 1] = arg.attr("type");
            R.set(arg, "index", i);
        });
        FunDeclarationNode tempCurrFun = null;
        if (scope.declarations.get(node.function.contents()) instanceof FunDeclarationNode){
            tempCurrFun = ((FunDeclarationNode) scope.declarations.get(node.function.contents()));
        }
        final FunDeclarationNode currFun = tempCurrFun;
        /*if (currFun.templateParameters == null && node.templateArgs != null){
            R.error("Try to give types as argument but no template parameter was declared", node, node);
        } -> TODO add it to prevent funCall<Int> without template has been specified*/
        HashMap<String, Type> templateParametersDictionnary = new HashMap<>();
        if (currFun != null && node.templateArgs != null) {
            if (node.templateArgs.size() != 0 && currFun.getTemplateParameters() != null && node.templateArgs.size() == currFun.getTemplateParameters().size()) {
                int tempIdx = 0;
                int templateNameIdx = 0;
                for (TemplateParameterNode templateName : currFun.getTemplateParameters()) {
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
                        /*case "Array":
                            TODO
                         */
                        default:
                            //r.errorFor(format("This type %s is not allowed for the Template function", actualType), node);
                            template = null;
                    }
                    String returnnName = currFun.returnType.contents();
                    if (returnnName.equals("T") || returnnName.charAt(0) == ('T') && Character.isDigit(returnnName.charAt(1))){
                        if (returnTemplateDic.get(node.function.contents()) == null){
                            returnTemplateDic.put(node.function.contents(), new ArrayList<>());
                            returnCounterFunction.put(node.function.contents(), 0);
                        }
                        if (templateName.name.equals(returnnName))
                            returnTemplateDic.get(node.function.contents()).add(template);
                    }
                    templateParametersDictionnary.put(templateName.name, template);
                    //here
                    //System.out.println(templateName.name);
                    //System.out.println(globalTypeDictionary + ""+variableToTemplate+""+ temp);
                    templateParametersDictionnary.put(templateName.name+"[]", new ArrayType(template, null));
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
        R.rule(node, "type")
            .using(dependencies)
            .by(r -> {
                Type maybeFunType = r.get(0);
                if (!(maybeFunType instanceof FunType)) {
                    r.error("trying to call a non-function expression: " + node.function.contents(), node.function);
                    return;
                }
                if (currFun != null && currFun.getTemplateParameters() != null) {
                    if (node.templateArgs != null && node.templateArgs.size() != 0) {
                        if(node.templateArgs.size() != currFun.getTemplateParameters().size()){
                            r.error(format("Wrong number of template arguments in %s: expected %d but got %d", node.function.contents(), currFun.getTemplateParameters().size(), node.templateArgs.size()), node.function);
                            return;
                        }
                    }else{
                        r.error("Trying to call template function without giving any types in arg: " + node.function.contents(), node.function);
                        return;
                    }
                }else if(currFun != null && node.templateArgs != null  && node.templateArgs.size() != 0){
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
                //TemplateType.INSTANCE.templateList = new HashMap<>();
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
                if (scopeFunc == null || (templateFromVarLeft == null && templateFromVarRight == null && leftList == null && rightList == null)){
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
                    System.out.println(left +   " " + right);
                    List<Type> typesToSet = new ArrayList<>();
                    if (globalTypeDictionary.size() == 0){
                        r.set(0, typesToSet);
                        return;
                    }
                    for (int i = 0; i < globalTypeDictionary.get(scopeFunc.name).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(scopeFunc.name).get(i);
                        left = leftList != null ? leftList.get(i): left;
                        right = rightList != null ? rightList.get(i): right;
                        left = templateFromVarLeft == null ? left : localHashmap.get(templateFromVarLeft);
                        right = templateFromVarRight == null ? right : localHashmap.get(templateFromVarRight);
                        if (node.operator == ADD && (left instanceof StringType || right instanceof StringType))
                            //r.set(0, StringType.INSTANCE);
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
        //String left_name = ((ReferenceNode) node.left).name;
        //String right_name =((ReferenceNode) node.right).name;
        //IntLiteralNode left_node = (IntLiteralNode) ((VarDeclarationNode) (((Scope) R.get(node.left,"scope")).declarations.get(left_name))).initializer;
        //List left_array = left_node.components;
        //IntLiteralNode right_node = (IntLiteralNode) ((VarDeclarationNode) (((Scope) R.get(node.right,"scope")).declarations.get(right_name))).initializer;
        //List right_array = right_node.components;
        //System.out.println("arr ar " +left_name +" " + left_node);
        //System.out.println("arr ar " +right_name +" " + right_node);
        //System.out.println("bin ar");
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
        if (isComparison(node.array_operator) || isEquality(node.array_operator) || isBoolOp(node.array_operator)){
            if (temp){
                r.set(0,new ArrayType(BoolType.INSTANCE,"Template[]"));
            }else{
                typeToSet.add(new ArrayType(BoolType.INSTANCE,null));
                r.set(0,new ArrayType(BoolType.INSTANCE,null));
            }
        }
        else{

            if (temp){
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
        //R.set(node,"scope",curr_scope);
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
        Boolean left_template = ((ArrayType) left).templateName != null && ((ArrayType) left).templateName.equals("Template[]");
        Boolean right_template = ((ArrayType) right).templateName != null && ((ArrayType) right).templateName.equals("Template[]");
        boolean temp = left_template || right_template;
        /*System.out.println(((ArrayType) left).componentType instanceof StringType);
        System.out.println(((ArrayType) right).componentType instanceof StringType);
        System.out.println(!temp);
        System.out.println(((ArrayType) left).templateName);
        System.out.println(((ArrayType) right).templateName);*/
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
        //String fun_name =((ExpressionNode)((FunCallNode )inferenceContext).function).contents();
        /*if (!(left_template && right_template)){
            System.out.println("not");
            System.out.println(((ArrayType) left).templateName);
        } //&& (((ArrayType) left).templateName)[0]=="T")*/




        if (!temp &&! left.name().equals(right.name())){
            if (left_name != null && right_name != null) {
                boolean left_num = left_name.equals("Int[]") || left_name.equals("Float[]");
                boolean right_num = right_name.equals("Int[]") || right_name.equals("Float[]");
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

        /*
        DeclarationNode left_decl =(((Scope) R.get(node.left,"scope")).declarations.get(left_name));
        DeclarationNode right_decl=(((Scope) R.get(node.right,"scope")).declarations.get(right_name));
        ArrayLiteralNode left_node = null;
        List<HashMap<String, ArrayLiteralNode>> left_nodes = null;
        ParameterNode left_param_node =null;
        if (left_decl instanceof VarDeclarationNode){
            System.out.println("left vardelc");
            left_node = (ArrayLiteralNode) (((VarDeclarationNode) left_decl).initializer);
        }else if (left_decl instanceof ParameterNode){
            System.out.println("left param");
            //String fun_name =((ExpressionNode)((FunCallNode )inferenceContext).function).contents();
            if (fun_name.equals("print")){
                String ref_name =((BinaryExpressionNode)((FunCallNode)inferenceContext).arguments.get(0)).right.contents();
                String ref_fun_name =((FunCallNode)((VarDeclarationNode)curr_scope.declarations.get(ref_name)).initializer).function.contents();

                left_nodes = param_arrays.get(ref_fun_name);
            }else {
                System.out.println("left else " + fun_name);
                left_nodes = param_arrays.get(fun_name);
            }

        }

        //System.out.println((VarDeclarationNode) (((Scope) R.get(node.right,"scope")).declarations.get(right_name)));
        ArrayLiteralNode right_node = null;//(ArrayLiteralNode) ((VarDeclarationNode) (((Scope) R.get(node.right,"scope")).declarations.get(right_name))).initializer;
        List<HashMap> right_nodes =null;
        ParameterNode right_param_node=null;
        if (right_decl instanceof VarDeclarationNode){
            right_node = (ArrayLiteralNode) ((VarDeclarationNode) (((Scope) R.get(node.right,"scope")).declarations.get(right_name))).initializer;
        }else if (right_decl instanceof ParameterNode){
            //right_param_node = (ParameterNode) right_decl;
            //String fun_name =((ExpressionNode)((FunCallNode )inferenceContext).function).contents();
            if (fun_name.equals("print")){
                String ref_name =((BinaryExpressionNode)((FunCallNode)inferenceContext).arguments.get(0)).right.contents();
                String ref_fun_name =((FunCallNode)((VarDeclarationNode)curr_scope.declarations.get(ref_name)).initializer).function.contents();
                left_nodes = param_arrays.get(ref_fun_name);
            }else {
                left_nodes = param_arrays.get(fun_name);
            }
        }
        System.out.println(right_node+" "+ right_nodes+" "+left_node+" "+left_nodes);
        System.out.println(param_arrays);

        //normal arrays
        if (right_node != null && left_node != null){
            if (check_arrays(r,left_node.components, right_node.components,temp, node, op)){

                set_array_type(r,node,left,temp);
                return;
            }

        }else if (right_node==null && left_node==null){ //parameter array
            //String fun_name =((ExpressionNode)((FunCallNode )inferenceContext).function).contents();
            System.out.println("here1");
            if (right_nodes!= null && left_nodes!= null){

                int len = param_arrays.get(fun_name).size();
                List right_check;
                List left_check;
                for (int i=0; i< len; i++){
                    right_check = param_arrays.get(fun_name).get(i).get(right_name).components;
                    left_check=param_arrays.get(fun_name).get(i).get(left_name).components;
                    check_arrays(r,left_check,right_check,temp,node,op);
                }
                set_array_type(r,node,left,temp);
                return;
            }
            else if (right_nodes ==null && left_nodes ==null){

                System.out.println(left_name+" "+((Scope) R.get(node.left,"scope")).declarations.get(left_name));
                ArrayLiteralNode left_n = (ArrayLiteralNode) ((VarDeclarationNode) (((Scope) R.get(node.left,"scope")).declarations.get(left_name))).initializer;
                List left_array = left_n.components;
                //System.out.println((VarDeclarationNode) (((Scope) R.get(node.right,"scope")).declarations.get(right_name)));
                ArrayLiteralNode right_n= (ArrayLiteralNode) ((VarDeclarationNode) (((Scope) R.get(node.right,"scope")).declarations.get(right_name))).initializer;
                List right_array = right_n.components;
                //List left_arr = new ArrayList();
                //left_arr.add();//(ArrayLiteralNode) (((VarDeclarationNode) scope.declarations.get(left_name)).initializer));
                //List right_arr= new ArrayList();
                //right_arr.add((ArrayLiteralNode) (((VarDeclarationNode) scope.declarations.get(right_name)).initializer));
                check_arrays(r,left_array,right_array,temp,node,op);
                set_array_type(r,node,left,temp);
                return;
            }

        }if (right_node ==null || left_node == null){

            List to_check;
            if (right_node==null){
                System.out.println("here2");
                if (!fun_name.equals("print")){
                    System.out.println("here 22");
                    int len = param_arrays.get(fun_name).size();
                    for (int i=0; i<len; i++){
                        System.out.println(fun_name+" "+ right_name+" " + param_arrays);
                        System.out.println(param_arrays.get(fun_name).get(i));
                        to_check = param_arrays.get(fun_name).get(i).get(right_name).components;
                        if (left_node != null){

                            check_arrays(r,left_node.components,to_check,temp,node,op);
                        }
                    }
                }

            }else {
                System.out.println("here3");
                int len = param_arrays.get(fun_name).size();
                for (int i=0; i<len; i++){
                    to_check = param_arrays.get(fun_name).get(i).get(left_name).components;
                    check_arrays(r,to_check,right_node.components,temp,node,op);
                }
            }
            set_array_type(r,node,left,temp);
        }*/



        /*List left_array = left_node.components;
        List right_array = right_node.components;*/
        //System.out.println("arr ar " +left_name +" " + left_array);
        //System.out.println("arr ar " +right_name +" " + right_array);
        //System.out.println(((BinaryExpressionNode)(((FunCallNode) inferenceContext).arguments.get(0))).right);

        /*//arrays of different sizes
        if (left_array.size() != right_array.size()){
            r.error(format("Trying to use @ between arrays of different lengths : %s and %s", left_array.size(),right_array.size()),node);
            return;
        }
        // types check for template[]
        if (temp){
            for (int i=0; i < left_array.size(); i++){

                boolean left_numeric = (leftsys_array.get(i) instanceof IntLiteralNode || left_array.get(i) instanceof FloatLiteralNode);
                boolean right_numeric = (right_array.get(i) instanceof IntLiteralNode || right_array.get(i) instanceof FloatLiteralNode);

                if ((left_numeric != right_numeric) && (left_array.get(i).getClass() != right_array.get(i).getClass())){
                    r.error("Trying to use @ between template arrays of different types",node);
                    return;
                }
                if (!left_numeric && !(this.string_op.contains(op))){
                    r.error(format("Trying to use illegal operation %s between strings in template arrays",op),node);
                    return;
                }
            }

        }*/

        /*ArrayType arrayType = (ArrayType) left;
        if (isComparison(node.array_operator)){
            if (temp){
                r.set(0,new ArrayType(BoolType.INSTANCE,"Template"));
            }
            r.set(0,new ArrayType(BoolType.INSTANCE,null));
        }
        else{
            if (temp){
                r.set(0,new ArrayType(arrayType.componentType,"Template"));
            }
            r.set(0,new ArrayType(arrayType.componentType,null));
        }*/

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
                if (templateFromVarRight != null || templateFromVarLeft != null|| rightList != null){
                    if (globalTypeDictionary.size() == 0){
                        r.set(0, left);
                        return;
                    }
                    for (int i = 0; i < globalTypeDictionary.get(scopeFunc.name).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(scopeFunc.name).get(i);
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
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        if (a instanceof IntType && b instanceof FloatType)
            return true;

        if (a instanceof ArrayType){
            if (b.name().equals("Template[]")){
                return true;
            }
            return b instanceof ArrayType
                && isAssignableTo(((ArrayType)a).componentType, ((ArrayType)b).componentType);
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
            }else{
                HashMap<String, String> temp = new HashMap<>();
                temp.put(node.name, node.type.contents());
                variableToTemplate.put(scopeFunc.name, temp);
            }
        } // TODO when var is declared with template in fun add it to variable template to be able to recognize it -> done but not checked !
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
                String templateFromVarLeft = expected instanceof TemplateType? node.type.contents(): null;
                String templateFromVarRight = scopeFunc != null ? variableToTemplate.get(scopeFunc.name).get(node.initializer.contents()): null;
                String funName = scopeFunc != null ? scopeFunc.name: null;
                if(node.initializer instanceof FunCallNode){
                    templateFromVarRight = "FunCall";
                    funName = ((FunCallNode) node.initializer).function.contents();
                }
                if (templateFromVarRight != null || templateFromVarLeft != null || actualList != null){
                    if (globalTypeDictionary.size() == 0)
                        return;
                    if(node.initializer instanceof FunCallNode){
                        //TODO OK ou pas ?
                        if (returnCounterFunction.size()==0){
                            return;
                        }
                        int iter = returnCounterFunction.get(funName);
                        actual = returnTemplateDic.get(funName).get(iter);
                        expected = templateFromVarLeft == null ? expected : globalTypeDictionary.get(funName).get(iter).get(templateFromVarLeft);
                        returnCounterFunction.put(funName, iter+1);
                        if (!isAssignableTo(actual, expected)) {
                            r.error(format(
                                    "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                                    node.name, expected, actual),
                                node.initializer);
                        }
                    }else{

                        for (int i = 0; i < globalTypeDictionary.get(funName).size(); i++) {
                            HashMap<String, Type> localHashmap = globalTypeDictionary.get(funName).get(i);
                            actual = (actualList != null && actualList.size()!=0)? actualList.get(i): actual;
                            expected = templateFromVarLeft == null ? expected : localHashmap.get(templateFromVarLeft);
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
        //scope = new Scope(node, scope);

        scope.declare(node.name, node);
        scope = new Scope(node, scope);

        R.set(node, "scope", scope);
        if (declaredFunNames.contains(node.name())){
            R.error(new SemanticError("Try to declare function with already existing name: "  + node.name(),null,node));
            return;
        }else {
            declaredFunNames.add(node.name());
        }
        variableToTemplate.put(node.name, new HashMap<>());
        HashMap<String, String> tempVariableToTemplate = variableToTemplate.get(node.name);
        for (ParameterNode param : node.parameters){
            String type = param.type.contents();
            if (type.equals("T") || type.charAt(0) == ('T') && Character.isDigit(type.charAt(1)) || type.contains("[")){
                tempVariableToTemplate.put(param.name, type);
            }
        }
        Attribute[] dependencies;
        dependencies = new Attribute[node.parameters.size() + 1];
        dependencies[0] = node.returnType.attr("value");
        forEachIndexed(node.parameters, (i, param) ->
            //dependencies[i + 1] = new Attribute(param, "type"));
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
                    //paramTypes[i] = IntType.INSTANCE;
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
                if (templateFromVar != null || typeList != null){
                    if (globalTypeDictionary.size() == 0)
                        return;
                    for (int i = 0; i < globalTypeDictionary.get(scopeFunc.name).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(scopeFunc.name).get(i);
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
                if (templateFromVar != null || typeList != null){
                    if(globalTypeDictionary.size() == 0)
                        return;
                    for (int i = 0; i < globalTypeDictionary.get(scopeFunc.name).size(); i++) {
                        HashMap<String, Type> localHashmap = globalTypeDictionary.get(scopeFunc.name).get(i);
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