package ua.yuriih.rustlexer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        FileInputStream inputStream = new FileInputStream(args[0]);
        Lexer lexer = new Lexer(new BufferedInputStream(inputStream));

        List<Token> tokens = lexer.parse();
        for (Token token : tokens) {
            if (token.value != null)
                System.out.printf("%d:%d\t%s\t%s\n", token.line, token.column, token.type, token.value);
            else
                System.out.printf("%d:%d\t%s\n", token.line, token.column, token.type);
        }
    }
}
