package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class BinaryExpressionNode extends ExpressionNode
{
    public final ExpressionNode left, right;
    //public final ArrayLiteralNode left_arr, right_arr;
    public final BinaryOperator operator;
    public final BinaryOperator array_operator;

    public BinaryExpressionNode (Span span, Object left,  Object operator, Object right, Object array_operator){//},Object left_arr, Object right_arr) {
        super(span);
        this.left = left==null ? null: Util.cast(left, ExpressionNode.class);
        //this.left_arr = left_arr==null ? null: Util.cast(left_arr,ArrayLiteralNode.class);
        //this.right_arr = right_arr==null ? null: Util.cast(right_arr,ArrayLiteralNode.class);
        this.right = Util.cast(right, ExpressionNode.class);
        this.operator = Util.cast(operator, BinaryOperator.class);
        this.array_operator = array_operator==null ? null: Util.cast(array_operator,BinaryOperator.class);
        //System.out.println("left "+  this.left.contents());
        //System.out.println(((ArrayLiteralNode) this.left).components);
        /*System.out.println("operator "+this.operator);
        System.out.println("right "+this.right);
        System.out.println("array_op "+this.array_operator);*/
    }

    @Override public String contents ()
    {
        String candidate = String.format("%s %s %s",
            left.contents(), operator.string, right.contents());

        return candidate.length() <= contentsBudget()
            ? candidate
            : String.format("(?) %s (?)", operator.string);
    }
}
