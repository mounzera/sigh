package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.bytecode.Null;
import norswap.utils.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VarDeclarationNode extends DeclarationNode
{
    public final String name;
    public final TypeNode type;
    public final List templateType;
    public final ExpressionNode initializer;

    public VarDeclarationNode (Span span, Object templateType, Object name, Object type, Object initializer) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.type = Util.cast(type, TypeNode.class);

        if (templateType != null){
            this.templateType = Util.cast(templateType, List.class);
        }
        else {this.templateType=null;}
        System.out.println(initializer);
        this.initializer = Util.cast(initializer, ExpressionNode.class);
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

    @Override
    public List<TemplateParameterNode> getTemplateParameters () {
        return null;
    }
}
