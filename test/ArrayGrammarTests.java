import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

import static norswap.sigh.ast.BinaryOperator.*;

public class ArrayGrammarTests extends AutumnTestFixture {
    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    private static TemplateTypeNode templit (String t) { return  new TemplateTypeNode(null,t);}

    private static StringLiteralNode stringlit( String s) { return  new StringLiteralNode(null, s);}

    @Test
    public void testDeclarations() {
        rule = grammar.statement;
        successExpect("var a:Template[]=[]", new VarDeclarationNode(null,"a",
            new ArrayTypeNode(null,new SimpleTypeNode(null, "Template")),
            new ArrayLiteralNode(null,asList())));
        successExpect("var a:Template[]=[true, 1,\"a\", 1.0]", new VarDeclarationNode(null,"a",
            new ArrayTypeNode(null,new SimpleTypeNode(null, "Template")),
            new ArrayLiteralNode(null,asList(new ReferenceNode(null,"true"),intlit(1),stringlit("a"),floatlit(1.0)))));

    }

    @Test
    public void operation(){
        rule = grammar.expression;
        successExpect("[1,1]@(+)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            ADD));
        successExpect("[1,1]@(-)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            SUBTRACT));
        successExpect("[1,1]@(*)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            MULTIPLY));
        successExpect("[1,1]@(/)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            DIVIDE));
        successExpect("[1,1]@(%)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            REMAINDER));
        successExpect("[1,1]@(>)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            GREATER));
        successExpect("[1,1]@(>=)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),intlit(1))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            GREATER_EQUAL));
        successExpect("[1,1.0]@(<)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),floatlit(1.0))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            LOWER));
        successExpect("[1,true]@(<=)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),new ReferenceNode(null,"true"))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            LOWER_EQUAL));
        successExpect("[1,\"hello\"]@(==)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),stringlit("hello"))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            EQUALITY));
        //failure("[1,1]@(&&)[2,2]");
        //failure("[1,1]@(||)[2,2]");

        successExpect("[1,\"hello\"]@(!=)[2,2]", new BinaryExpressionNode(null,
            new ArrayLiteralNode(null, asList(intlit(1),stringlit("hello"))),
            ARRAY_OP,
            new ArrayLiteralNode(null, asList(intlit(2),intlit(2))),
            NOT_EQUALS));

    }
}

