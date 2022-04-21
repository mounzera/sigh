package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public final class ReferenceNode extends ExpressionNode
{
    public final String name;
    public FunDeclarationNode funName;


    public ReferenceNode (Span span, Object name) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.funName = null;

    }

    @Override public String contents() {
        return name;
    }
}
