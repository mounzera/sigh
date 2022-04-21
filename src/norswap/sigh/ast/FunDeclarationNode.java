package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.HashMap;
import java.util.List;

public class FunDeclarationNode extends DeclarationNode
{
    public final String name;
    public final List<ParameterNode> parameters;
    public final List<TemplateParameterNode> templateParameters;
    public Boolean isTemplate;
    public final TypeNode returnType;
    public final BlockNode block;

    @SuppressWarnings("unchecked")
    public FunDeclarationNode
            (Span span, Object templateParameters, Object name, Object parameters, Object returnType, Object block) {
        super(span);
        if (templateParameters != null){
            this.templateParameters = Util.cast(templateParameters, List.class);
            isTemplate = Boolean.TRUE;
        }else{
            this.templateParameters = null;
            isTemplate = Boolean.FALSE;
        }
        this.name = Util.cast(name, String.class);
        this.parameters = Util.cast(parameters, List.class);
        this.returnType = returnType == null
            ? new SimpleTypeNode(new Span(span.start, span.start), "Void")
            : Util.cast(returnType, TypeNode.class);
        this.block = Util.cast(block, BlockNode.class);

    }

    @Override public String name () {
        return name;
    }

    public List<TemplateParameterNode> getTemplateParameters(){
        return this.templateParameters;
    }


    @Override public String contents () {
        return "fun " + name;
    }

    @Override public String declaredThing () {
        return "function";
    }
}
