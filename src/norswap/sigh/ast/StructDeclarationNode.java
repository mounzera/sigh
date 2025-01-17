package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public class StructDeclarationNode extends DeclarationNode
{
    public final String name;
    public final List<FieldDeclarationNode> fields;
    public final List<TemplateParameterNode> templateParameters;

    @SuppressWarnings("unchecked")
    public StructDeclarationNode (Span span, Object templateParameters, Object name, Object fields) {
        super(span);
        this.name = Util.cast(name, String.class);
        this.fields = Util.cast(fields, List.class);
        this.templateParameters = templateParameters==null ? null:Util.cast(templateParameters, List.class);
    }

    @Override public String name () {
        return name;
    }

    public List<TemplateParameterNode> getTemplateParameters () {
        return templateParameters;
    }

    @Override public String contents () {
        return "struct " + name;
    }

    @Override public String declaredThing () {
        return "struct";
    }
}
