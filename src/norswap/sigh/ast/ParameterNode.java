package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public final class ParameterNode extends DeclarationNode
{
    public final String name;
    public final TypeNode type;

    public ParameterNode (Span span, Object name, Object type) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.type = Util.cast(type, TypeNode.class);
    }

    @Override public String name () {
        return name;
    }

    @Override public String contents () {
        return name;
    }

    public TypeNode getType(){return this.type;}

    @Override public String declaredThing () {
        return "parameter";
    }

    @Override
    public List<TemplateParameterNode> getTemplateParameters () {
        return null;
    }
}
