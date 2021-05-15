package ua.yuriih.rustlexer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Lexer {
    private BufferedInputStream in;

    private List<Token> tokens = new ArrayList<>();

    private State state;
    private State.StringEscape stringEscapeState;
    private int rawStringHashCount = 0;

    private StringBuilder buffer;
    private int bufferStartLine;
    private int bufferStartColumn;

    private int line;
    private int column;

    private static final HashMap<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("as", TokenType.AS);
        KEYWORDS.put("break", TokenType.BREAK);
        KEYWORDS.put("const", TokenType.CONST);
        KEYWORDS.put("continue", TokenType.CONTINUE);
        KEYWORDS.put("crate", TokenType.CRATE);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("enum", TokenType.ENUM);
        KEYWORDS.put("extern", TokenType.EXTERN);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("fn", TokenType.FN);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("impl", TokenType.IMPL);
        KEYWORDS.put("in", TokenType.IN);
        KEYWORDS.put("let", TokenType.LET);
        KEYWORDS.put("loop", TokenType.LOOP);
        KEYWORDS.put("match", TokenType.MATCH);
        KEYWORDS.put("mod", TokenType.MOD);
        KEYWORDS.put("move", TokenType.MOVE);
        KEYWORDS.put("mut", TokenType.MUT);
        KEYWORDS.put("pub", TokenType.PUB);
        KEYWORDS.put("ref", TokenType.REF);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("self", TokenType.SELF_VALUE);
        KEYWORDS.put("Self", TokenType.SELF_TYPE);
        KEYWORDS.put("static", TokenType.STATIC);
        KEYWORDS.put("struct", TokenType.STRUCT);
        KEYWORDS.put("super", TokenType.SUPER);
        KEYWORDS.put("trait", TokenType.TRAIT);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("type", TokenType.TYPE);
        KEYWORDS.put("unsafe", TokenType.UNSAFE);
        KEYWORDS.put("use", TokenType.USE);
        KEYWORDS.put("where", TokenType.WHERE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("async", TokenType.ASYNC);
        KEYWORDS.put("await", TokenType.AWAIT);
        KEYWORDS.put("dyn", TokenType.DYN);
        KEYWORDS.put("abstract", TokenType.ABSTRACT);
        KEYWORDS.put("become", TokenType.BECOME);
        KEYWORDS.put("box", TokenType.BOX);
        KEYWORDS.put("do", TokenType.DO);
        KEYWORDS.put("final", TokenType.FINAL);
        KEYWORDS.put("macro", TokenType.MACRO);
        KEYWORDS.put("override", TokenType.OVERRIDE);
        KEYWORDS.put("priv", TokenType.PRIV);
        KEYWORDS.put("typeof", TokenType.TYPEOF);
        KEYWORDS.put("unsized", TokenType.UNSIZED);
        KEYWORDS.put("virtual", TokenType.VIRTUAL);
        KEYWORDS.put("yield", TokenType.YIELD);
        KEYWORDS.put("try", TokenType.TRY);
//        KEYWORDS.put("union", TokenType.UNION);
        KEYWORDS.put("'static", TokenType.STATIC_LIFETIME);
    }


    public Lexer(BufferedInputStream in) {
        this.in = in;
    }

    public List<Token> parse() throws IOException {
        while (true) {
            int read = in.read();
            if (read < 0)
                return tokens;

            char c = (char) read;
            if (c == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }

            switch (state) {
                case INITIAL -> initialState(c);
                case ID_OR_UNDERSCORE -> idOrUnderscore(c);
                case ID_OR_KEYWORD -> idOrKeyword(c);
                case POSSIBLY_RAW_STRING -> possiblyRawString(c);
                case POSSIBLY_BYTE_OR_BYTE_STRING -> possiblyByteOrByteString(c);
                case CHAR_LITERAL_OR_LIFETIME_OR_LABEL -> charLiteralOrLifetimeOrLabel(c);
                case LIFETIME_OR_LABEL -> lifetimeOrLabel(c);
                case STRING_LITERAL, CHAR_LITERAL -> stringOrCharOrByteLiteral(c, true, true);
                case BYTE_LITERAL, BYTE_STRING_LITERAL -> stringOrCharOrByteLiteral(c, false, false);
            }
        }
    }

    private boolean isIdentifierChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private boolean isHexDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private void startBufferAndSet(char c, State state) {
        buffer = new StringBuilder();
        buffer.append(c);
        this.state = state;
        bufferStartLine = line;
        bufferStartColumn = column;
    }

    private void addEmpty(TokenType type) {
        tokens.add(new Token(bufferStartLine, bufferStartColumn, type));
    }

    private void addWithData(TokenType type, String data) {
        tokens.add(new Token(bufferStartLine, bufferStartColumn, type, data));
    }

    private void initialState(char c) {
        if (c == '_') {
            startBufferAndSet(c, State.ID_OR_UNDERSCORE);
        } else if (isIdentifierChar(c)) {
            startBufferAndSet(c, State.ID_OR_KEYWORD);
        } else if (c == 'r') {
            startBufferAndSet(c, State.POSSIBLY_RAW_STRING);
        } else if (c == '"') {
            startBufferAndSet(c, State.STRING_LITERAL);
        } else if (c == 'b') {
            startBufferAndSet(c, State.POSSIBLY_BYTE_OR_BYTE_STRING);
        } else if (c == '\'') {
            startBufferAndSet(c, State.CHAR_LITERAL_OR_LIFETIME_OR_LABEL);
        } else if (c >= '0' && c <= '9') {
            startBufferAndSet(c, State.NUMBER_LITERAL);
        } else if (c == '/') {
            startBufferAndSet(c, State.SLASH);
        } else if (!Character.isWhitespace(c)) {
            startBufferAndSet(c, State.PUNCTUATION);
        }
    }

//    private int peekNext() throws IOException {
//        in.mark(1);
//        int nextChar = in.read();
//        in.reset();
//        return nextChar;
//    }

    private void idOrUnderscore(char c) {
        if (isIdentifierChar(c)) {
            state = State.ID_OR_KEYWORD;
            buffer.append(c);
        } else {
            addEmpty(TokenType.UNDERSCORE);
            state = State.INITIAL;
        }
    }

    private void idOrKeyword(char c) {
        if (isIdentifierChar(c)) {
            buffer.append(c);
        } else {
            //Special case for weak keyword "union"
            if (!tokens.isEmpty()) {
                Token token = tokens.get(tokens.size() - 1);
                if (token.type == TokenType.IDENTIFIER && token.value.equals("union")) {
                    tokens.remove(tokens.size() - 1);
                    tokens.add(new Token(token.line, token.column, TokenType.UNION));
                }
            }

            String s = buffer.toString();
            TokenType keywordType = KEYWORDS.get(s);
            if (keywordType != null)
                addEmpty(keywordType);
            else
                addWithData(TokenType.IDENTIFIER, s);
            state = State.INITIAL;
            initialState(c);
        }
    }

    private void possiblyRawString(char c) {
        if (c == '"') {
            state = State.RAW_STRING_LITERAL;
            rawString(c);
        } else {
            state = State.ID_OR_KEYWORD;
            idOrKeyword(c);
        }
    }

    private void possiblyByteOrByteString(char c) {
        if (c == '\'') {
            buffer.append(c);
            state = State.BYTE_LITERAL;
        } else if (c == '"') {
            buffer.append(c);
            state = State.BYTE_STRING_LITERAL;
        } else if (c == 'r') {
            buffer.append(c);
            state = State.RAW_STRING_LITERAL;
        } else {
            state = State.ID_OR_KEYWORD;
            idOrKeyword(c);
        }
    }

    /**
     * We could have either:
     * 'a'       - char literal
     * '\n'      - also char literal
     * 'myLoop:  - loop label
     * 'lifetime - lifetime parameter
     */
    private void charLiteralOrLifetimeOrLabel(char c) {
        if (c == '\\' && buffer.length() == 1) {
            buffer.append(c);
            state = State.CHAR_LITERAL;
            stringEscapeState = State.StringEscape.SLASH;
        } else if (c == '\'') {
            buffer.append(c);
            if (buffer.length() == 2) {
                addWithData(TokenType.ERROR, "Empty char literal");
            } else if (buffer.length() == 3) {
                addWithData(TokenType.CHAR_LITERAL, buffer.toString());
            }
        } else if (buffer.length() == 1) {
            buffer.append(c);
        } else {
            state = State.LIFETIME_OR_LABEL;
            lifetimeOrLabel(c);
        }
    }

    private void lifetimeOrLabel(char c) {
        if (isIdentifierChar(c)) {
            buffer.append(c);
        } else if (c == ':') {
            buffer.append(c);
            addWithData(TokenType.LABEL, buffer.toString());
            state = State.INITIAL;
        } else {
            addWithData(TokenType.LIFETIME, buffer.toString());
            state = State.INITIAL;
        }
    }

    private void stringOrCharOrByteLiteral(
            char c,
            boolean isAscii,
            boolean allowUnicodeEscape
    ) {
//        if (c == '\\') {
//            buffer.append(c);
//            if (stringEscapeState == LexerState.StringEscape.SLASH)
//                stringEscapeState = LexerState.StringEscape.NONE;
//            else if (stringEscapeState == LexerState.StringEscape.NONE)
//                stringEscapeState = LexerState.StringEscape.SLASH;
//            else
//                addWithData(TokenType.ERROR, "Unexpected slash in literal escape sequence");
//            return;
//        }
        switch (stringEscapeState) {
            case NONE -> escapeNone(c);
            case SLASH -> escapeSlash(c, allowUnicodeEscape);
            case ASCII_OR_BYTE -> escapeAsciiOrByte(c, isAscii);
            case UNICODE -> escapeUnicode(c);
        }
    }

    private void escapeNone(char c) {
        buffer.append(c);
        if (c == '\'') {
            if (state == State.CHAR_LITERAL) {
                addWithData(TokenType.CHAR_LITERAL, buffer.toString());
                state = State.INITIAL;
            } else if (state == State.BYTE_LITERAL) {
                addWithData(TokenType.BYTE_LITERAL, buffer.toString());
                state = State.INITIAL;
            }
        } else if (c == '"') {
            if (state == State.STRING_LITERAL) {
                addWithData(TokenType.STRING_LITERAL, buffer.toString());
                state = State.INITIAL;
            } else if (state == State.BYTE_STRING_LITERAL) {
                addWithData(TokenType.BYTE_STRING_LITERAL, buffer.toString());
                state = State.INITIAL;
            }
        } else if (c == '\\') {
            stringEscapeState = State.StringEscape.SLASH;
        }
    }

    private void escapeSlash(char c, boolean allowUnicodeEscape) {
        buffer.append(c);
        switch (c) {
            case '\'', '"', 'n', 'r', 't', '\\', '0' -> {
                stringEscapeState = State.StringEscape.NONE;
            }
            case '\n' -> {
                if (state != State.STRING_LITERAL) {
                    stringEscapeState = State.StringEscape.NONE;
                    state = State.INITIAL;
                    addWithData(TokenType.ERROR, "Backslash before newline is only possible in string literals.");
                }
            }
            case 'x' -> {
                stringEscapeState = State.StringEscape.ASCII_OR_BYTE;
            }
            case 'u' -> {
                if (allowUnicodeEscape) {
                    stringEscapeState = State.StringEscape.UNICODE;
                } else {
                    addWithData(TokenType.ERROR, "Unicode escape sequences are not allowed in byte strings.");
                    stringEscapeState = State.StringEscape.NONE;
                    state = State.INITIAL;
                }
            }
        }
    }

    private void escapeAsciiOrByte(char c, boolean isAscii) {
        if (buffer.charAt(buffer.length() - 1) == 'x') {
            buffer.append(c);
            if (isHexDigit(c)) {
                if (!isAscii) {
                    stringEscapeState = State.StringEscape.NONE;
                    state = State.INITIAL;
                    addWithData(TokenType.ERROR, "Unexpected 7 (ASCII escape sequence character code can't be higher than 7F)");
                }
            } else if (!(c >= '0' && c <= '7')) {
                stringEscapeState = State.StringEscape.NONE;
                state = State.INITIAL;
                addWithData(TokenType.ERROR, "Unexpected symbol in hex character code.");
            }
        } else if (buffer.charAt(buffer.length() - 2) == 'x') {
            buffer.append(c);
            if (!isHexDigit(c)) {
                stringEscapeState = State.StringEscape.NONE;
                state = State.INITIAL;
                addWithData(TokenType.ERROR, "Unexpected symbol in hex character code.");
            }
        } else {
            stringEscapeState = State.StringEscape.NONE;
            escapeNone(c);
        }
    }

    private void escapeUnicode(char c) {
        buffer.append(c);
        if (buffer.charAt(buffer.length() - 1) == 'u') {
            if (c != '{') {
                stringEscapeState = State.StringEscape.NONE;
                state = State.INITIAL;
                addWithData(TokenType.ERROR, "Unicode escape sequence must start with {");
            }
        } else {
            int currentDigits = 0;
            while (buffer.charAt(buffer.length() - 1 - currentDigits) != '{')
                currentDigits++;

            if (isHexDigit(c)) {
                if (currentDigits + 1 > 6) {
                    stringEscapeState = State.StringEscape.NONE;
                    state = State.INITIAL;
                    addWithData(TokenType.ERROR, "Too many digits in Unicode escape sequence");
                }
            } else if (c == '}') {
                stringEscapeState = State.StringEscape.NONE;
            } else {
                stringEscapeState = State.StringEscape.NONE;
                state = State.INITIAL;
                addWithData(TokenType.ERROR, "Unexpected symbol in hex character code.");
            }
        }
    }

    private void rawString(char c) {
        
    }
}
