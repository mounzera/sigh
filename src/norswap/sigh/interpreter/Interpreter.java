package norswap.sigh.interpreter;

import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Reactor;
import norswap.utils.Util;
import norswap.utils.exceptions.Exceptions;
import norswap.utils.exceptions.NoStackException;
import norswap.utils.visitors.ValuedVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static norswap.sigh.ast.BinaryOperator.*;
import static norswap.sigh.ast.BinaryOperator.LOWER_EQUAL;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.coIterate;
import static norswap.utils.Vanilla.map;

/**
 * Implements a simple but inefficient interpreter for Sigh.
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>The compiled code currently doesn't support closures (using variables in functions that
 *     are declared in some surroudning scopes outside the function). The top scope is supported.
 *     </li>
 * </ul>
 *
 * <p>Runtime value representation:
 * <ul>
 *     <li>{@code Int}, {@code Float}, {@code Bool}: {@link Long}, {@link Double}, {@link Boolean}</li>
 *     <li>{@code String}: {@link String}</li>
 *     <li>{@code null}: {@link Null#INSTANCE}</li>
 *     <li>Arrays: {@code Object[]}</li>
 *     <li>Structs: {@code HashMap<String, Object>}</li>
 *     <li>Functions: the corresponding {@link DeclarationNode} ({@link FunDeclarationNode} or
 *     {@link SyntheticDeclarationNode}), excepted structure constructors, which are
 *     represented by {@link Constructor}</li>
 *     <li>Types: the corresponding {@link StructDeclarationNode}</li>
 * </ul>
 */
public final class Interpreter
{
    // ---------------------------------------------------------------------------------------------

    private final ValuedVisitor<SighNode, Object> visitor = new ValuedVisitor<>();
    private final Reactor reactor;
    private ScopeStorage storage = null;
    private RootScope rootScope;
    private ScopeStorage rootStorage;

    //Template arrays
    private  String currentFunctionName=null;
    private List<ExpressionNode> currentArguments =null;
    private Return currentFunctionValue = null;

    // ---------------------------------------------------------------------------------------------

    public Interpreter (Reactor reactor) {
        this.reactor = reactor;

        // expressions
        visitor.register(IntLiteralNode.class,           this::intLiteral);
        visitor.register(FloatLiteralNode.class,         this::floatLiteral);
        visitor.register(StringLiteralNode.class,        this::stringLiteral);
        visitor.register(ReferenceNode.class,            this::reference);
        visitor.register(ConstructorNode.class,          this::constructor);
        visitor.register(ArrayLiteralNode.class,         this::arrayLiteral);
        visitor.register(ParenthesizedNode.class,        this::parenthesized);
        visitor.register(FieldAccessNode.class,          this::fieldAccess);
        visitor.register(ArrayAccessNode.class,          this::arrayAccess);
        visitor.register(FunCallNode.class,              this::funCall);
        visitor.register(UnaryExpressionNode.class,      this::unaryExpression);
        visitor.register(BinaryExpressionNode.class,     this::binaryExpression);
        visitor.register(AssignmentNode.class,           this::assignment);

        // statement groups & declarations
        visitor.register(RootNode.class,                 this::root);
        visitor.register(BlockNode.class,                this::block);
        visitor.register(VarDeclarationNode.class,       this::varDecl);
        // no need to visitor other declarations! (use fallback)

        // statements
        visitor.register(ExpressionStatementNode.class,  this::expressionStmt);
        visitor.register(IfNode.class,                   this::ifStmt);
        visitor.register(WhileNode.class,                this::whileStmt);
        visitor.register(ReturnNode.class,               this::returnStmt);

        visitor.registerFallback(node -> null);
    }

    // ---------------------------------------------------------------------------------------------

    public Object interpret (SighNode root) {
        try {
            return run(root);
        } catch (PassthroughException e) {
            throw Exceptions.runtime(e.getCause());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object run (SighNode node) {
        try {
            return visitor.apply(node);
        } catch (InterpreterException | Return | PassthroughException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterpreterException("exception while executing " + node, e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to implement the control flow of the return statement.
     */
    private static class Return extends NoStackException {
        final Object value;
        private Return (Object value) {
            this.value = value;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private <T> T get(SighNode node) {
        return cast(run(node));
    }

    // ---------------------------------------------------------------------------------------------

    private Long intLiteral (IntLiteralNode node) {
        return node.value;
    }

    private Double floatLiteral (FloatLiteralNode node) {
        return node.value;
    }

    private String stringLiteral (StringLiteralNode node) {
        return node.value;
    }

    // ---------------------------------------------------------------------------------------------

    private Object parenthesized (ParenthesizedNode node) {
        reactor.set(node,"scope",rootScope);

        return get(node.expression);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] arrayLiteral (ArrayLiteralNode node) {
        return map(node.components, new Object[0], visitor);
    }

    // ---------------------------------------------------------------------------------------------
    //Returns an array with the types of the left and right nodes
    private Type[] returnType(BinaryExpressionNode node){
        Type leftType = null;
        Type rightType = null;
        try{
            List<Type> left  = reactor.get(node.left, "type");
            leftType = left.get(0);
        }catch (Exception e){
            leftType  = reactor.get(node.left, "type");
        }
        try{
            List<Type> right  = reactor.get(node.left, "type");
            rightType = right.get(0);
        }catch (Exception e){
            rightType  = reactor.get(node.left, "type");
        }
        Object left  = get(node.left);
        Object right = get(node.right);
        try{
            double test = (double) right;
            rightType = FloatType.INSTANCE;
        }catch (Exception e){}
        try{
            double test = (double) left;
            leftType = FloatType.INSTANCE;
        }catch (Exception e){}
        try{
            String test = (String) left;
            leftType = StringType.INSTANCE;
        }catch (Exception e){}
        try{
            String test = (String) right;
            rightType = StringType.INSTANCE;
        }catch (Exception e){}

        Type[] ret_types = {leftType,rightType};
        return ret_types;

    }
    private Boolean isFloat(Type leftType,Type rightType){
        return (leftType instanceof FloatType || rightType instanceof FloatType) &&(!(leftType instanceof  StringType) &&!(rightType instanceof  StringType));
    }

    //Binary expression for non array types
    private Object literalBinaryExpression(BinaryExpressionNode node){
        /*Type leftType  = reactor.get(node.left, "type");
        Type rightType = reactor.get(node.right, "type");*/

        // Cases where both operands should not be evaluated.
        switch (node.operator) {
            case OR:  return booleanOp(node, false);
            case AND: return booleanOp(node, true);
        }
        Object left  = get(node.left);
        Object right = get(node.right);
        /*try{
            double test = (double) right;
            rightType = FloatType.INSTANCE;
        }catch (Exception e){}
        try{
            double test = (double) left;
            leftType = FloatType.INSTANCE;
        }catch (Exception e){}
        try{
            String test = (String) left;
            leftType = StringType.INSTANCE;
        }catch (Exception e){}
        try{
            String test = (String) right;
            rightType = StringType.INSTANCE;
        }catch (Exception e){}
        */
        Type[] ret_types = returnType(node);
        Type leftType = ret_types[0];
        Type rightType = ret_types[1];
        if (//node.operator == BinaryOperator.ADD &&
            (leftType instanceof StringType || rightType instanceof StringType)){
            switch (node.operator){
                case ADD:
                    return convertToString(left) + convertToString(right);
                case GREATER:
                    return convertToString(left).compareTo( convertToString(right)) >0;
                case GREATER_EQUAL:
                    return convertToString(left).compareTo( convertToString(right)) >=0;
                case LOWER:
                    return convertToString(left).compareTo( convertToString(right)) <0;
                case LOWER_EQUAL:
                    return convertToString(left).compareTo( convertToString(right)) <=0;
                case EQUALITY:
                    return convertToString(left).compareTo( convertToString(right)) ==0;
                case NOT_EQUALS:
                    return convertToString(left).compareTo( convertToString(right)) !=0;
                default:
                    throw new Error("should not reach here");
            }
        }

        boolean floating = leftType instanceof FloatType || rightType instanceof FloatType;
        boolean numeric  = floating || leftType instanceof IntType || leftType instanceof TemplateType || rightType instanceof TemplateType;

        if (numeric)
            return numericOp(node, floating, (Number) left, (Number) right);

        switch (node.operator) {
            case EQUALITY:
                return  leftType.isPrimitive() ? left.equals(right) : left == right;
            case NOT_EQUALS:
                return  leftType.isPrimitive() ? !left.equals(right) : left != right;
        }

        throw new Error("should not reach here");
    }
    //Template[]
    private boolean isComparison (BinaryOperator op) {
        return op == GREATER || op == GREATER_EQUAL || op == LOWER || op == LOWER_EQUAL;
    }
    private Object binaryExpression (BinaryExpressionNode node)
    {

        // Normal Binary expression
        if (!(node.operator.equals(BinaryOperator.ARRAY_OP))){
            return literalBinaryExpression(node);
        }
        //Object[] left  = (Object[]) get(node.left);
        //Object right = (Object[]) get(node.right);

        // Array Binary expression
        Scope scope = reactor.get(node.left, "scope");
        /*String left_name = ((ReferenceNode) node.left).name;
        String right_name =((ReferenceNode) node.right).name;*/
        String left_name = null;
        String right_name = null;
        if (node.left instanceof ReferenceNode){
            left_name=((ReferenceNode) node.left).name;
        }
        if (node.right instanceof ReferenceNode){
            right_name=((ReferenceNode) node.right).name;
        }

        ArrayLiteralNode left_arr = null; // retrieve lesft anr right arrays
        ArrayLiteralNode right_arr = null;
        if (node.left instanceof  ParenthesizedNode){
            ArrayLiteralNode result = (ArrayLiteralNode) binaryExpression((BinaryExpressionNode) ((ParenthesizedNode) node.left).expression);
            left_arr = result;
        }
        if (node.left instanceof BinaryExpressionNode){
            left_arr = (ArrayLiteralNode) binaryExpression((BinaryExpressionNode) node.left);
        }
        if (node.right instanceof  ParenthesizedNode){
            ArrayLiteralNode result = (ArrayLiteralNode) binaryExpression((BinaryExpressionNode) ((ParenthesizedNode) node.right).expression);
            right_arr = result;
        }
        if (node.right instanceof BinaryExpressionNode){
            right_arr = (ArrayLiteralNode) binaryExpression((BinaryExpressionNode) node.right);
        }

        FunDeclarationNode currFunction = null;//(FunDeclarationNode) scope.lookup(currentFunctionName).declaration;//(FunDeclarationNode) curr_scope.declarations.get(currentFunctionName);
        Scope curr_scope =null;//(Scope)reactor.get(scope.declarations.get(left_name),"scope");
        ArrayLiteralNode[] parameter_arrays = new ArrayLiteralNode[2]; //store left and right arrays
        if (scope != null && currentFunctionName != null) {
            currFunction = (FunDeclarationNode) scope.lookup(currentFunctionName).declaration;//(FunDeclarationNode) curr_scope.declarations.get(currentFunctionName);
            if (scope.declarations.get(left_name) != null)
                curr_scope= (Scope) reactor.get(scope.declarations.get(left_name), "scope");
            System.out.println(curr_scope);
        }
        if (currFunction != null) {
            int param_index = 0;
            for (ParameterNode p : currFunction.parameters) {
                if (node.left instanceof  ReferenceNode && p.name.equals(((ReferenceNode) node.left).name)) {
                    if (currentArguments.get(param_index) instanceof ArrayLiteralNode) {
                        parameter_arrays[0] = (ArrayLiteralNode) currentArguments.get(param_index);
                    } else if (currentArguments.get(param_index) instanceof ReferenceNode) {
                        String param_name = ((ReferenceNode) currentArguments.get(param_index)).name;
                        VarDeclarationNode array_decl = (VarDeclarationNode) (curr_scope.declarations.get(param_name));
                        if (array_decl.initializer instanceof FunCallNode){
                            parameter_arrays[0] = (ArrayLiteralNode) currentFunctionValue.value;
                        }
                        else {
                            parameter_arrays[0] = (ArrayLiteralNode) (array_decl.initializer);
                        }

                    } else System.out.println("unknown parameter type array operation");
                }
                if (node.right instanceof ReferenceNode && p.name.equals(((ReferenceNode) node.right).name)) {
                    if (currentArguments.get(param_index) instanceof ArrayLiteralNode) {
                        parameter_arrays[1] = (ArrayLiteralNode) currentArguments.get(param_index);
                    } else if (currentArguments.get(param_index) instanceof ReferenceNode) {
                        String param_name = ((ReferenceNode) currentArguments.get(param_index)).name;
                        System.out.println(node.right +" "+ node.left + " "+ curr_scope + " " +param_name);

                        VarDeclarationNode array_decl = (VarDeclarationNode) (curr_scope.declarations.get(param_name));
                        if (array_decl.initializer instanceof FunCallNode){
                            parameter_arrays[1] = (ArrayLiteralNode) currentFunctionValue.value;
                        }
                        else {
                            parameter_arrays[1] = (ArrayLiteralNode) (array_decl.initializer);
                        }
                    }
                }
                param_index++;
            }
        }
        /*if (currFunction == null){
            if (curr_scope.declarations.get("print") instanceof  FunDeclarationNode){
            currFunction=(FunDeclarationNode) curr_scope.declarations.get("print");
            }
        }*/
        System.out.println("scope "+scope+ right_name);
        if (node.left instanceof ReferenceNode){
            left_arr = parameter_arrays[0];//(ArrayLiteralNode) (((VarDeclarationNode) scope.declarations.get(left_name)).initializer);

            if (left_arr == null) { //array declared not a reference but a declaration
                left_arr = (ArrayLiteralNode) (((VarDeclarationNode) scope.declarations.get(left_name)).initializer);
            }
        }
        if (node.right instanceof ReferenceNode) {
            right_arr = parameter_arrays[1];//(ArrayLiteralNode) (((VarDeclarationNode) scope.declarations.get(right_name)).initializer);
            if (right_arr == null) { // arrays is a declaration
                right_arr = (ArrayLiteralNode) (((VarDeclarationNode) scope.declarations.get(right_name)).initializer);
            }
        }
        //}
        //TODO check span

        //type check
        if (left_arr.components.size() != right_arr.components.size()){

            throw  new Error(format(" Operation between arrays of different length: %s (%d) and %s (%d)",left_arr.components.toString(), left_arr.components.size(), right_arr.components.toString(),right_arr.components.size()));
        }


        //result computation
        List<ExpressionNode> result = new ArrayList<>();
        int len = left_arr.components.size();
        for (int i=0; i< len;i++){
            BinaryExpressionNode new_node = new BinaryExpressionNode(null,left_arr.components.get(i),node.array_operator,right_arr.components.get(i),null);
            if(isComparison(node.array_operator)){
                result.add(new ReferenceNode(null, String.valueOf(literalBinaryExpression(new_node))));
            }else {
                Type[] ret_types=returnType(new_node); //deduce the types of the nodes
                boolean floating = isFloat(ret_types[0],ret_types[1]);
                switch (node.array_operator) {
                    case OR:
                        result.add(new ReferenceNode(null, String.valueOf(booleanOp(new_node, false))));
                    case AND:
                        result.add(new ReferenceNode(null, String.valueOf(booleanOp(new_node, true))));
                    default:
                        if (floating){
                            result.add(new FloatLiteralNode(null, ((Double) literalBinaryExpression(new_node))));
                        }else if( ret_types[0]==StringType.INSTANCE && ret_types[1]==StringType.INSTANCE){
                            result.add(new StringLiteralNode(null, (String) literalBinaryExpression(new_node)));
                        }
                        else if(ret_types[0] instanceof IntType && ret_types[1] instanceof  IntType) {
                            IntLiteralNode toAdd =new IntLiteralNode(null, (long) literalBinaryExpression(new_node));
                            result.add(toAdd);
                            reactor.set(toAdd,"type",IntType.INSTANCE);
                        }
                        else {
                            throw new Error(format("trying to use @ between array elements of incompatible types : %s and %s", ret_types[0].name(),ret_types[1].name()));
                        }
                }

            }



        }//TODO Span
        ArrayLiteralNode resultNode = new ArrayLiteralNode(null,result);
        return resultNode;



    }

    // ---------------------------------------------------------------------------------------------

    private boolean booleanOp (BinaryExpressionNode node, boolean isAnd)
    {
        boolean left = get(node.left);
        return isAnd
                ? left && (boolean) get(node.right)
                : left || (boolean) get(node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private Object numericOp
            (BinaryExpressionNode node, boolean floating, Number left, Number right)
    {
        long ileft, iright;
        double fleft, fright;

        if (floating) {
            fleft  = left.doubleValue();
            fright = right.doubleValue();
            ileft = iright = 0;
        } else {
            ileft  = left.longValue();
            iright = right.longValue();
            fleft = fright = 0;
        }

        Object result;
        if (floating)
            switch (node.operator) {
                case MULTIPLY:      return fleft *  fright;
                case DIVIDE:        return fleft /  fright;
                case REMAINDER:     return fleft %  fright;
                case ADD:           return fleft +  fright;
                case SUBTRACT:      return fleft -  fright;
                case GREATER:       return fleft >  fright;
                case LOWER:         return fleft <  fright;
                case GREATER_EQUAL: return fleft >= fright;
                case LOWER_EQUAL:   return fleft <= fright;
                case EQUALITY:      return fleft == fright;
                case NOT_EQUALS:    return fleft != fright;
                default:
                    throw new Error("should not reach here");
            }
        else
            switch (node.operator) {
                case MULTIPLY:      return ileft *  iright;
                case DIVIDE:        return ileft /  iright;
                case REMAINDER:     return ileft %  iright;
                case ADD:           return ileft +  iright;
                case SUBTRACT:      return ileft -  iright;
                case GREATER:       return ileft >  iright;
                case LOWER:         return ileft <  iright;
                case GREATER_EQUAL: return ileft >= iright;
                case LOWER_EQUAL:   return ileft <= iright;
                case EQUALITY:      return ileft == iright;
                case NOT_EQUALS:    return ileft != iright;
                default:
                    throw new Error("should not reach here");
            }
    }

    // ---------------------------------------------------------------------------------------------

    public Object assignment (AssignmentNode node)
    {
        if (node.left instanceof ReferenceNode) {
            Scope scope = reactor.get(node.left, "scope");
            String name = ((ReferenceNode) node.left).name;
            Object rvalue = get(node.right);
            assign(scope, name, rvalue, reactor.get(node, "type"));
            return rvalue;
        }

        if (node.left instanceof ArrayAccessNode) {
            ArrayAccessNode arrayAccess = (ArrayAccessNode) node.left;
            Object[] array = getNonNullArray(arrayAccess.array);
            int index = getIndex(arrayAccess.index);
            try {
                return array[index] = get(node.right);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        if (node.left instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) node.left;
            Object object = get(fieldAccess.stem);
            if (object == Null.INSTANCE)
                throw new PassthroughException(
                    new NullPointerException("accessing field of null object"));
            Map<String, Object> struct = cast(object);
            Object right = get(node.right);
            struct.put(fieldAccess.fieldName, right);
            return right;
        }

        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private int getIndex (ExpressionNode node)
    {
        long index = get(node);
        if (index < 0)
            throw new ArrayIndexOutOfBoundsException("Negative index: " + index);
        if (index >= Integer.MAX_VALUE - 1)
            throw new ArrayIndexOutOfBoundsException("Index exceeds max array index (2ˆ31 - 2): " + index);
        return (int) index;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] getNonNullArray (ExpressionNode node)
    {
        Object object = get(node);
        if (object == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null array"));
        return (Object[]) object;
    }

    // ---------------------------------------------------------------------------------------------

    private Object unaryExpression (UnaryExpressionNode node)
    {
        // there is only NOT
        assert node.operator == UnaryOperator.NOT;
        return ! (boolean) get(node.operand);
    }

    // ---------------------------------------------------------------------------------------------

    private Object arrayAccess (ArrayAccessNode node)
    {
        Object[] array = getNonNullArray(node.array);
        try {
            return array[getIndex(node.index)];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new PassthroughException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object root (RootNode node)
    {
        assert storage == null;
        rootScope = reactor.get(node, "scope");
        storage = rootStorage = new ScopeStorage(rootScope, null);
        storage.initRoot(rootScope);

        try {
            node.statements.forEach(this::run);
        } catch (Return r) {
            return r.value;
            // allow returning from the main script
        } finally {
            storage = null;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void block (BlockNode node) {
        Scope scope = reactor.get(node, "scope");
        storage = new ScopeStorage(scope, storage);
        node.statements.forEach(this::run);
        storage = storage.parent;
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Constructor constructor (ConstructorNode node) {
        // guaranteed safe by semantic analysis
        return new Constructor(get(node.ref));
    }

    // ---------------------------------------------------------------------------------------------

    private Object expressionStmt (ExpressionStatementNode node) {
        get(node.expression);
        return null;  // discard value
    }

    // ---------------------------------------------------------------------------------------------

    private Object fieldAccess (FieldAccessNode node)
    {
        Object stem = get(node.stem);
        if (stem == Null.INSTANCE)
            throw new PassthroughException(
                new NullPointerException("accessing field of null object"));
        return stem instanceof Map
                ? Util.<Map<String, Object>>cast(stem).get(node.fieldName)
                : (long) ((Object[]) stem).length; // only field on arrays
    }

    // ---------------------------------------------------------------------------------------------

    private Object funCall (FunCallNode node)
    {
        currentFunctionName=node.function.contents();
        currentArguments =node.arguments;
        //reactor.set(node,"scope",reactor.get(node,"scope"));
        Object decl = get(node.function);
        node.arguments.forEach(this::run);
        Object[] args = map(node.arguments, new Object[0], visitor);

        if (decl == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("calling a null function"));

        if (decl instanceof SyntheticDeclarationNode)
            return builtin(((SyntheticDeclarationNode) decl).name(), args);

        if (decl instanceof Constructor)
            return buildStruct(((Constructor) decl).declaration, args);

        ScopeStorage oldStorage = storage;
        Scope scope = reactor.get(decl, "scope");
        storage = new ScopeStorage(scope, storage);

        FunDeclarationNode funDecl = (FunDeclarationNode) decl;
        coIterate(args, funDecl.parameters,
                (arg, param) -> storage.set(scope, param.name, arg));

        try {
            get(funDecl.block);
        } catch (Return r) {
            return r.value;
        } finally {
            storage = oldStorage;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object builtin (String name, Object[] args)
    {
        assert name.equals("print"); // only one at the moment
        String out = convertToString(args[0]);
        System.out.println(out);
        return out;
    }

    // ---------------------------------------------------------------------------------------------

    private String convertToString (Object arg)
    {
        if (arg == Null.INSTANCE)
            return "null";
        else if (arg instanceof Object[])
            return Arrays.deepToString((Object[]) arg);
        else if (arg instanceof FunDeclarationNode)
            return ((FunDeclarationNode) arg).name;
        else if (arg instanceof StructDeclarationNode)
            return ((StructDeclarationNode) arg).name;
        else if (arg instanceof Constructor)
            return "$" + ((Constructor) arg).declaration.name;
        else
            return arg.toString();
    }

    // ---------------------------------------------------------------------------------------------

    private HashMap<String, Object> buildStruct (StructDeclarationNode node, Object[] args)
    {
        HashMap<String, Object> struct = new HashMap<>();
        for (int i = 0; i < node.fields.size(); ++i)
            struct.put(node.fields.get(i).name, args[i]);
        return struct;
    }

    // ---------------------------------------------------------------------------------------------

    private Void ifStmt (IfNode node)
    {
        if (get(node.condition))
            get(node.trueStatement);
        else if (node.falseStatement != null)
            get(node.falseStatement);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void whileStmt (WhileNode node)
    {
        while (get(node.condition))
            get(node.body);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object reference (ReferenceNode node)
    {
        Scope scope = reactor.get(node, "scope");
        DeclarationNode decl = reactor.get(node, "decl");

        if (decl instanceof VarDeclarationNode
        || decl instanceof ParameterNode
        || decl instanceof SyntheticDeclarationNode
                && ((SyntheticDeclarationNode) decl).kind() == DeclarationKind.VARIABLE)
            return scope == rootScope
                ? rootStorage.get(scope, node.name)
                : storage.get(scope, node.name);

        return decl; // structure or function
    }

    // ---------------------------------------------------------------------------------------------

    private Void returnStmt (ReturnNode node) {
        Return r = new Return(node.expression == null ? null : get(node.expression));
        currentFunctionValue = r;
        throw r ;
    }

    // ---------------------------------------------------------------------------------------------

    private Void varDecl (VarDeclarationNode node)
    {
        Scope scope = reactor.get(node, "scope");
        assign(scope, node.name, get(node.initializer), reactor.get(node, "type"));
        reactor.set(node.initializer,"context",node.name);
        //reactor.set(context,node.na);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private void assign (Scope scope, String name, Object value, Type targetType)
    {
        if (value instanceof Long && targetType instanceof FloatType)
            value = ((Long) value).doubleValue();
        storage.set(scope, name, value);
    }

    // ---------------------------------------------------------------------------------------------
}
