package norswap.sigh.types;

public final class ArrayType extends Type
{
    public Type componentType;
    public final String templateName;

    public ArrayType (Type componentType, String templateName) {
        this.componentType = componentType;
        this.templateName = templateName;
    }

    @Override public String name() {
        return componentType.toString() + "[]";
    }

    @Override public boolean equals (Object o) {
        return this == o || o instanceof ArrayType && componentType.equals(o);
    }

    @Override public int hashCode () {
        return componentType.hashCode();
    }
}
