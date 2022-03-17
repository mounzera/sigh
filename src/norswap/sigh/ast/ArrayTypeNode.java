package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class ArrayTypeNode extends TypeNode
{
    public final TypeNode componentType;
    public String templateName;

    public ArrayTypeNode (Span span, Object componentType) {
        super(span);
        this.componentType = Util.cast(componentType, TypeNode.class);
        String temp =  Util.cast(this.componentType.contents(), String.class);
        this.templateName = this.componentType.contents();
    }

    public void setTemplateName (String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName () {
        return templateName;
    }

    @Override public String contents() {
        return componentType.contents() + "[]";
    }
}
