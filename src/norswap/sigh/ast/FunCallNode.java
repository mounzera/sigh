package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.types.Type;
import norswap.utils.Util;
import java.util.List;

public final class FunCallNode extends ExpressionNode
{
    public final ExpressionNode function;
    public final List<ExpressionNode> arguments;
    public final List<TypeNode> templateArgs;

    @SuppressWarnings("unchecked")
    public FunCallNode (Span span, Object function, Object templateArguments, Object arguments) {
        super(span);
        if (templateArguments != null){
            this.templateArgs = Util.cast(templateArguments, List.class);
        }else{
            this.templateArgs = null;
        }

        this.function = Util.cast(function, ExpressionNode.class);
        this.arguments = Util.cast(arguments, List.class);
    }

    @Override public String contents ()
    {
        String args = arguments.size() == 0 ? "()" : "(...)";
        return function.contents() + args;
    }
}
