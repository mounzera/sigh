package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.types.TemplateType;

public class TemplateTypeNode extends TypeNode {
    public TemplateType type;
    public String name;
    public TemplateTypeNode(Span span, String name) {
        super(span);
        this.type = TemplateType.INSTANCE;
        this.name = name;
    }

    @Override
    public String contents() {
        return type.name();
    }
}
