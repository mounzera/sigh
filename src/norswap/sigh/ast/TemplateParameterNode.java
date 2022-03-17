package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

import java.util.List;

public class TemplateParameterNode extends DeclarationNode{

    public final String name;
    public final TemplateTypeNode type;

    public TemplateParameterNode(Span span, Object parameters, Object type) {
        super(span);
        this.name = Util.cast(parameters, String.class);
        this.type = Util.cast(type, TemplateTypeNode.class);
    }

    @Override
    public String contents() {
        return name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String declaredThing() {
        return "Template" + type;
    }
}
