package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public final class ParameterNode extends DeclarationNode
{
    public final String name;
    public TypeNode type;
    public ArrayLiteralNode array;
    public int id =0;

    public ParameterNode (Span span, Object name, Object type) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.type = Util.cast(type, TypeNode.class);
        System.out.println(id);
    }


    @Override public String name () {
        return name;
    }

    @Override public String contents () {
        return name;
    }

    @Override public String declaredThing () {
        return "parameter";
    }
}
