package norswap.sigh.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TemplateType extends Type{

    public static final TemplateType INSTANCE = new TemplateType();
    public List<String> templateList = new ArrayList<>();
    private TemplateType () {

    }

    @Override public boolean isPrimitive () {
        return false;
    }
    public String getParamName(int index){
        return templateList.get(index);
    }

    public void pushParamName(String name){
        this.templateList.add(name);
    }
    @Override
    public String name() {
        return "Template";
    }
}
