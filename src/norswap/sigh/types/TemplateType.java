package norswap.sigh.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TemplateType extends Type{

    public static final TemplateType INSTANCE = new TemplateType();
    public HashMap<String, List<String>> templateList = new HashMap<>();


    @Override public boolean isPrimitive () {
        return false;
    }
    public String getParamName(String key, int index){
        List<String> tempList = templateList.get(key);
        return tempList.get(index);
    }

    public void pushParamName(String funName, String param){
        List<String> tempList = this.templateList.computeIfAbsent(funName, k -> new ArrayList<>());
        tempList.add(param);
    }
    @Override
    public String name() {
        return "Template";
    }
}
