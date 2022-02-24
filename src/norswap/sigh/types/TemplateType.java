package norswap.sigh.types;

public class TemplateType extends Type{

    public static final TemplateType INSTANCE = new TemplateType();
    private TemplateType () {}

    @Override public boolean isPrimitive () {
        return false;
    }
    @Override
    public String name() {
        return "Template";
    }
}
