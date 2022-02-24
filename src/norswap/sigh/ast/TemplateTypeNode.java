package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.types.TemplateType;

public class TemplateTypeNode extends TypeNode {
    public TemplateType type;
    public TemplateTypeNode(Span span) {
        super(span);
        this.type = TemplateType.INSTANCE;
    }

    @Override
    public String contents() {
        return type.name();
    }
}
