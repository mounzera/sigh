package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

import java.util.List;

public class TemplateParameterNode extends DeclarationNode{

    public final String parameter;
    public final TemplateTypeNode type;

    public TemplateParameterNode(Span span, Object parameters, Object type) {
        super(span);
        this.parameter = Util.cast(parameters, String.class);
        this.type = Util.cast(type, TemplateTypeNode.class);
    }

    @Override
    public String contents() {
        return "template " + parameter;
    }

    @Override
    public String name() {
        return parameter;
    }

    @Override
    public String declaredThing() {
        return "Template parameter";
    }
}
