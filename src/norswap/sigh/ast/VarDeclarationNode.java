package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.bytecode.Null;
import norswap.sigh.types.ArrayType;
import norswap.utils.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VarDeclarationNode extends DeclarationNode
{
    public final String name;
    public final TypeNode type;
    public final ExpressionNode initializer;
    public String context;


    public VarDeclarationNode (Span span, Object name, Object type, Object initializer) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.type = Util.cast(type, TypeNode.class);
        this.initializer = Util.cast(initializer, ExpressionNode.class);

        /*System.out.println(span);
        System.out.println(this.name);
        System.out.println(((ArrayTypeNode)this.type).componentType);
        System.out.println(((ArrayLiteralNode)this.initializer).components.get(0));*/
    }

    @Override public String name () {
        return name;
    }

    @Override public String contents () {
        return "var " + name;
    }

    @Override public String declaredThing () {
        return "variable";
    }
}
