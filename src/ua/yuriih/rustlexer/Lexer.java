package ua.yuriih.rustlexer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public final class Lexer {
    private final InputStream in;

    private final ArrayList<Token> tokens = new ArrayList<>();

    private State state = State.INITIAL;
    private State.StringEscape stringEscapeState = State.StringEscape.NONE;
    private int rawStringHashCount = 0;
    private int rawStringEndHashCount = 0;
    private int nestedCommentDepth = 0; //block comments can be nested
    private State outerCommentState; //block comments can be nested

    private StringBuilder buffer;
    private int bufferStartLine;
    private int bufferStartColumn;

    private int line = 0;
    private int column = 0;

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
        //weak keywords are handled separately
//        KEYWORDS.put("union", TokenType.UNION);
//        KEYWORDS.put("'static", TokenType.STATIC_LIFETIME);
    }


    public Lexer(InputStream in) {
        this.in = in;
    }

    public ArrayList<Token> parse() throws IOException {
        char c = 0;
        while (true) {
            int read = in.read();
            if (read < 0) {
                //make sure to end with EOL
                if (c != '\n')
                    c = '\n';
                else
                    return tokens;
            } else {
                c = (char) read;
            }

//            System.err.printf("%d:%d '%s' %s %s %d,%d %d(%s)\n", line, column, c, state,
//                    stringEscapeState, rawStringHashCount, rawStringEndHashCount,
//                    nestedCommentDepth, outerCommentState);

            switch (state) {
                case INITIAL -> initialState(c);
                case ID_OR_UNDERSCORE -> idOrUnderscore(c);
                case ID_OR_KEYWORD_OR_SUFFIX -> idOrKeywordOrSuffix(c);
                case MAYBE_RAW_STRING -> maybeRawString(c);
                case MAYBE_BYTE_OR_BYTE_STRING -> maybeByteOrByteString(c);
                case CHAR_LITERAL_OR_LIFETIME_OR_LABEL -> charLiteralOrLifetimeOrLabel(c);
                case LIFETIME_OR_LABEL -> lifetimeOrLabel(c);
                case STRING_LITERAL, CHAR_LITERAL_ESCAPED -> stringOrCharOrByteLiteral(c, false);
                case BYTE_LITERAL, BYTE_STRING_LITERAL -> stringOrCharOrByteLiteral(c, true);
                case CHAR_LITERAL_END -> charLiteralEnd(c);
                case BYTE_LITERAL_END -> byteLiteralEnd(c);
                case RAW_STRING_LITERAL_START -> rawStringLiteralStart(c);
                case RAW_STRING_LITERAL -> rawStringLiteral(c);
                case RAW_STRING_LITERAL_MAYBE_END -> rawStringLiteralMaybeEnd(c);
                case NUMBER_LITERAL -> numberLiteral(c);
                case NUMBER_LITERAL_START_ZERO -> numberLiteralStartZero(c);
                case INT_LITERAL_HEX -> intLiteralHex(c);
                case INT_LITERAL_OCT -> intLiteralOct(c);
                case INT_LITERAL_BIN -> intLiteralBin(c);
                case INT_LITERAL_HEX_NO_DIGITS -> intLiteralHexNoDigits(c);
                case INT_LITERAL_OCT_NO_DIGITS -> intLiteralOctNoDigits(c);
                case INT_LITERAL_BIN_NO_DIGITS -> intLiteralBinNoDigits(c);
                case FLOAT_LITERAL_DOT -> floatLiteralDot(c);
                case FLOAT_LITERAL_EXPONENT -> floatLiteralExponent(c);
                case FLOAT_LITERAL_EXPONENT_START -> floatLiteralExponentStart(c);
                case FLOAT_LITERAL_EXPONENT_NO_DIGITS -> floatLiteralExponentNoDigits(c);
                case SLASH -> slash(c);
                case COMMENT_BLOCK -> commentBlock(c, TokenType.COMMENT);
                case COMMENT_BLOCK_START -> commentBlockStart(c);
                case COMMENT_BLOCK_MAYBE_OUTER_DOC_START -> commentBlockMaybeOuterDocStart(c);
                case COMMENT_BLOCK_INNER_DOC -> commentBlock(c, TokenType.COMMENT_INNER_DOC);
                case COMMENT_BLOCK_OUTER_DOC -> commentBlock(c, TokenType.COMMENT_OUTER_DOC);
                case COMMENT_LINE -> commentLine(c, TokenType.COMMENT);
                case COMMENT_LINE_START -> commentLineStart(c);
                case COMMENT_LINE_MAYBE_OUTER_DOC_START -> commentLineMaybeOuterDocStart(c);
                case COMMENT_LINE_INNER_DOC -> commentLine(c, TokenType.COMMENT_INNER_DOC);
                case COMMENT_LINE_OUTER_DOC -> commentLine(c, TokenType.COMMENT_OUTER_DOC);
                case PLUS -> plus(c);
                case MINUS -> minus(c);
                case STAR -> star(c);
                case PERCENT -> percent(c);
                case CARET -> caret(c);
                case NOT -> not(c);
                case AND -> and(c);
                case OR -> or(c);
                case LT -> lessThan(c);
                case GT -> greaterThan(c);
                case SHL -> shiftLeft(c);
                case SHR -> shiftRight(c);
                case EQ -> equals(c);
                case DOT -> dot(c);
                case DOT_DOT -> dotDot(c);
                case COLON -> colon(c);
            }

            if (c == '\n') {
                line++;
                column = 0;
            } else {
                column++;
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

    private void emptyFromCurrentPosAndReset(TokenType type) {
        tokens.add(new Token(line, column, type));
        state = State.INITIAL;
    }

    private void addEmptyAndReset(TokenType type) {
        tokens.add(new Token(bufferStartLine, bufferStartColumn, type));
        state = State.INITIAL;
    }

    private void addAndReset(TokenType type, String data) {
        tokens.add(new Token(bufferStartLine, bufferStartColumn, type, data));
        state = State.INITIAL;
    }

    private void addAndReset(TokenType type) {
        addAndReset(type, buffer.toString());
    }

    private void errorAndReset(String errorMessage) {
        addAndReset(TokenType.ERROR, errorMessage);
    }

    private void errorAtBufferStart(String errorMessage) {
        tokens.add(new Token(bufferStartLine, bufferStartColumn, TokenType.ERROR, errorMessage));
    }

    private void initialState(char c) {
        if (c == '_') {
            startBufferAndSet(c, State.ID_OR_UNDERSCORE);
        } else if (c == 'r') {
            startBufferAndSet(c, State.MAYBE_RAW_STRING);
        } else if (c == '"') {
            startBufferAndSet(c, State.STRING_LITERAL);
        } else if (c == 'b') {
            startBufferAndSet(c, State.MAYBE_BYTE_OR_BYTE_STRING);
        } else if (c == '\'') {
            startBufferAndSet(c, State.CHAR_LITERAL_OR_LIFETIME_OR_LABEL);
        } else if (c == '0') {
            startBufferAndSet(c, State.NUMBER_LITERAL_START_ZERO);
        } else if (c >= '1' && c <= '9') {
            startBufferAndSet(c, State.NUMBER_LITERAL);
        } else if (isIdentifierChar(c)) {
            startBufferAndSet(c, State.ID_OR_KEYWORD_OR_SUFFIX);
        } else if (c == '/') {
            startBufferAndSet(c, State.SLASH);
        } else if (c == '+') {
            startBufferAndSet(c, State.PLUS);
        } else if (c == '-') {
            startBufferAndSet(c, State.MINUS);
        } else if (c == '*') {
            startBufferAndSet(c, State.STAR);
        } else if (c == '%') {
            startBufferAndSet(c, State.PERCENT);
        } else if (c == '^') {
            startBufferAndSet(c, State.CARET);
        } else if (c == '!') {
            startBufferAndSet(c, State.NOT);
        } else if (c == '&') {
            startBufferAndSet(c, State.AND);
        } else if (c == '|') {
            startBufferAndSet(c, State.OR);
        } else if (c == '<') {
            startBufferAndSet(c, State.LT);
        } else if (c == '>') {
            startBufferAndSet(c, State.GT);
        } else if (c == '=') {
            startBufferAndSet(c, State.EQ);
        } else if (c == '.') {
            startBufferAndSet(c, State.DOT);
        } else if (c == ':') {
            startBufferAndSet(c, State.COLON);
        } else if (c == '@') {
            emptyFromCurrentPosAndReset(TokenType.AT);
        } else if (c == ',') {
            emptyFromCurrentPosAndReset(TokenType.COMMA);
        } else if (c == ';') {
            emptyFromCurrentPosAndReset(TokenType.SEMICOLON);
        } else if (c == '#') {
            emptyFromCurrentPosAndReset(TokenType.POUND);
        } else if (c == '$') {
            emptyFromCurrentPosAndReset(TokenType.DOLLAR);
        } else if (c == '?') {
            emptyFromCurrentPosAndReset(TokenType.QUESTION);
        } else if (c == '(') {
            emptyFromCurrentPosAndReset(TokenType.PAREN_L);
        } else if (c == ')') {
            emptyFromCurrentPosAndReset(TokenType.PAREN_R);
        } else if (c == '[') {
            emptyFromCurrentPosAndReset(TokenType.SQUARE_L);
        } else if (c == ']') {
            emptyFromCurrentPosAndReset(TokenType.SQUARE_R);
        } else if (c == '{') {
            emptyFromCurrentPosAndReset(TokenType.CURLY_L);
        } else if (c == '}') {
            emptyFromCurrentPosAndReset(TokenType.CURLY_R);
        } else if (!Character.isWhitespace(c)) {
            errorAndReset("Unexpected symbol: " + c);
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
            state = State.ID_OR_KEYWORD_OR_SUFFIX;
            buffer.append(c);
        } else {
            addEmptyAndReset(TokenType.UNDERSCORE);
            state = State.INITIAL;
        }
    }

    private void idOrKeywordOrSuffix(char c) {
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
                addEmptyAndReset(keywordType);
            else
                addAndReset(TokenType.IDENTIFIER, s);
            initialState(c);
        }
    }

    private void maybeRawString(char c) {
        if (c == '"' || c == '#') {
            state = State.RAW_STRING_LITERAL_START;
            rawStringLiteralStart(c);
        } else {
            state = State.ID_OR_KEYWORD_OR_SUFFIX;
            idOrKeywordOrSuffix(c);
        }
    }

    private void maybeByteOrByteString(char c) {
        if (c == '\'') {
            buffer.append(c);
            state = State.BYTE_LITERAL;
        } else if (c == '"') {
            buffer.append(c);
            state = State.BYTE_STRING_LITERAL;
        } else if (c == 'r') {
            buffer.append(c);
            state = State.MAYBE_RAW_STRING;
        } else {
            state = State.ID_OR_KEYWORD_OR_SUFFIX;
            idOrKeywordOrSuffix(c);
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
            state = State.CHAR_LITERAL_ESCAPED;
            stringEscapeState = State.StringEscape.SLASH;
        } else if (c == '\'') {
            buffer.append(c);
            if (buffer.length() == 2) {
                errorAndReset("Empty char literal");
            } else if (buffer.length() == 3) {
                addAndReset(TokenType.CHAR_LITERAL);
            }
        } else if (buffer.length() == 1) {
            buffer.append(c);
        } else {
            if (!isIdentifierChar(buffer.charAt(buffer.length() - 1))) {
                buffer.append(c);
                errorAndReset("Unexpected character in char literal, lifetime, or label: " + c);
            } else if (!isIdentifierChar(c)) {
                buffer.append(c);
                errorAndReset("Unexpected character in char literal, lifetime, or label: " + c);
            } else {
                state = State.LIFETIME_OR_LABEL;
                lifetimeOrLabel(c);
            }
        }
    }

    private void lifetimeOrLabel(char c) {
        if (isIdentifierChar(c)) {
            buffer.append(c);
        } else if (c == ':') {
            buffer.append(c);
            addAndReset(TokenType.LABEL);
        } else {
            String s = buffer.toString();
            if (s.equals("'static"))
                addEmptyAndReset(TokenType.STATIC_LIFETIME);
            else
                addAndReset(TokenType.LIFETIME);
            initialState(c);
        }
    }

    private void stringOrCharOrByteLiteral(
            char c,
            boolean isByte
    ) {
        switch (stringEscapeState) {
            case NONE -> escapeNone(c, isByte);
            case SLASH -> escapeSlash(c, isByte);
            case ASCII_OR_BYTE -> escapeAsciiOrByte(c, isByte);
            case UNICODE -> escapeUnicode(c);
        }
    }

    private void escapeNone(char c, boolean isByte) {
        if (c >= 128 && isByte) {
            errorAtBufferStart("Unexpected character in byte string: " + c);
            return;
        }

        buffer.append(c);

        if (c == '"') {
            if (state == State.STRING_LITERAL)
                addAndReset(TokenType.STRING_LITERAL);
            else if (state == State.BYTE_STRING_LITERAL)
                addAndReset(TokenType.BYTE_STRING_LITERAL);
        } else if (c == '\\') {
            stringEscapeState = State.StringEscape.SLASH;
        } else {
            if (state == State.CHAR_LITERAL_ESCAPED) {
                state = State.CHAR_LITERAL_END;
            } else if (state == State.BYTE_LITERAL) {
                state = State.BYTE_LITERAL_END;
            }
        }
    }

    private void escapeSlash(char c, boolean isByte) {
        buffer.append(c);
        switch (c) {
            case '\'', '"', 'n', 'r', 't', '\\', '0' -> {
                stringEscapeState = State.StringEscape.NONE;
            }
            case '\n' -> {
                if (state == State.STRING_LITERAL) {
                    stringEscapeState = State.StringEscape.NONE;
                } else {
                    errorAtBufferStart("Backslash before newline is only possible in string literals.");
                }
            }
            case 'x' -> {
                stringEscapeState = State.StringEscape.ASCII_OR_BYTE;
            }
            case 'u' -> {
                if (isByte) {
                    errorAtBufferStart("Unicode escape sequences are not allowed in byte strings.");
                    stringEscapeState = State.StringEscape.NONE;
                } else {
                    stringEscapeState = State.StringEscape.UNICODE;
                }
            }
        }
    }

    private void escapeAsciiOrByte(char c, boolean isByte) {
        if (buffer.charAt(buffer.length() - 1) == 'x') {
            buffer.append(c);
            if (isHexDigit(c)) {
                if (!(c >= '0' && c <= '7') && !isByte) {
                    errorAtBufferStart("Unexpected " + c + " (ASCII escape sequence character code can't be higher than 7F)");
                    stringEscapeState = State.StringEscape.NONE;
                }
            } else {
                errorAtBufferStart("Unexpected symbol in hex character code: " + c);
                stringEscapeState = State.StringEscape.NONE;
            }
        } else if (buffer.charAt(buffer.length() - 2) == 'x') {
            buffer.append(c);
            if (!isHexDigit(c)) {
                errorAtBufferStart("Unexpected symbol in hex character code: " + c);
                stringEscapeState = State.StringEscape.NONE;
            }
        } else {
            stringEscapeState = State.StringEscape.NONE;
            if (state == State.CHAR_LITERAL_ESCAPED) {
                state = State.CHAR_LITERAL_END;
                charLiteralEnd(c);
            } else if (state == State.BYTE_LITERAL) {
                state = State.BYTE_LITERAL_END;
                byteLiteralEnd(c);
            } else {
                escapeNone(c, isByte);
            }
        }
    }

    private void escapeUnicode(char c) {
        buffer.append(c);
        if (buffer.charAt(buffer.length() - 2) == 'u') {
            if (c != '{') {
                errorAtBufferStart("Unicode escape sequence must start with {");
                stringEscapeState = State.StringEscape.NONE;
            }
        } else {
            int currentDigits = 0;
            while (buffer.charAt(buffer.length() - 1 - currentDigits) != '{')
                currentDigits++;

            if (isHexDigit(c)) {
                if (currentDigits + 1 > 6) {
                    errorAtBufferStart("Too many digits in Unicode escape sequence");
                    stringEscapeState = State.StringEscape.NONE;
                }
            } else if (c == '}') {
                stringEscapeState = State.StringEscape.NONE;
            } else {
                errorAtBufferStart("Unexpected symbol in Unicode hex character code: " + c);
                stringEscapeState = State.StringEscape.NONE;
            }
        }
    }

    private void charLiteralEnd(char c) {
        buffer.append(c);
        if (c == '\'') {
            addAndReset(TokenType.CHAR_LITERAL);
        } else {
            errorAtBufferStart("Did not expect more than one character in char literal");
        }
    }

    private void byteLiteralEnd(char c) {
        buffer.append(c);
        if (c == '\'') {
            addAndReset(TokenType.BYTE_LITERAL);
        } else {
            errorAtBufferStart("Did not expect more than one byte in byte literal");
        }
    }

    private void rawStringLiteralStart(char c) {
        buffer.append(c);
        if (c == '#') {
            rawStringHashCount++;
        } else if (c == '"') {
            state = State.RAW_STRING_LITERAL;
        } else {
            errorAndReset("Unexpected character at start of raw string: " + c + " (expected \" or #)");
            rawStringHashCount = 0;
        }
    }

    private void rawStringLiteral(char c) {
        buffer.append(c);
        if (c == '"') {
            state = State.RAW_STRING_LITERAL_MAYBE_END;
        }
    }

    private void rawStringLiteralMaybeEnd(char c) {
        if (rawStringEndHashCount == rawStringHashCount) {
            rawStringHashCount = 0;
            rawStringEndHashCount = 0;

            TokenType type;
            if (buffer.charAt(0) == 'b')
                type = TokenType.RAW_BYTE_STRING_LITERAL;
            else
                type = TokenType.RAW_STRING_LITERAL;

            addAndReset(type);
            initialState(c);

        } else if (c == '#') {
            rawStringEndHashCount++;
            buffer.append(c);
        } else {
            rawStringEndHashCount = 0;
            state = State.RAW_STRING_LITERAL;
            buffer.append(c);
        }
    }

    private void numberLiteralStartZero(char c) {
        if (c == 'x') {
            buffer.append(c);
            state = State.INT_LITERAL_HEX_NO_DIGITS;
        } else if (c == 'o') {
            buffer.append(c);
            state = State.INT_LITERAL_OCT_NO_DIGITS;
        } else if (c == 'b') {
            buffer.append(c);
            state = State.INT_LITERAL_BIN_NO_DIGITS;
        } else {
            state = State.NUMBER_LITERAL;
            numberLiteral(c);
        }
    }

    private void numberLiteral(char c) {
        if (c == 'E') {
            buffer.append(c);
            state = State.FLOAT_LITERAL_EXPONENT_START;
        } else if (c == '.') {
            buffer.append(c);
            state = State.FLOAT_LITERAL_DOT;
        } else if ((c >= '0' && c <= '9') || c == '_') {
            buffer.append(c);
        } else {
            addAndReset(TokenType.INT_LITERAL_DEC);
            initialState(c);
        }
    }

    private void intLiteralHex(char c) {
        if (isHexDigit(c) || c == '_') {
            buffer.append(c);
        } else {
            addAndReset(TokenType.INT_LITERAL_HEX);
            initialState(c);
        }
    }

    private void intLiteralOct(char c) {
        if ((c >= '0' && c <= '8') || c == '_') {
            buffer.append(c);
        } else {
            addAndReset(TokenType.INT_LITERAL_OCTAL);
            initialState(c);
        }
    }

    private void intLiteralBin(char c) {
        if (c == '0' || c == '1' || c == '_') {
            buffer.append(c);
        } else {
            addAndReset(TokenType.INT_LITERAL_BIN);
            initialState(c);
        }
    }

    private void intLiteralHexNoDigits(char c) {
        if (isHexDigit(c)) {
            buffer.append(c);
            state = State.INT_LITERAL_HEX;
        } else if (c == '_') {
            buffer.append(c);
        } else {
            errorAndReset("Hex literal must contain at least one digit");
            initialState(c);
        }
    }

    private void intLiteralOctNoDigits(char c) {
        if (c >= '0' && c <= '9') {
            buffer.append(c);
            state = State.INT_LITERAL_OCT;
        } else if (c == '_') {
            buffer.append(c);
        } else {
            errorAndReset("Octal literal must contain at least one digit");
            initialState(c);
        }
    }

    private void intLiteralBinNoDigits(char c) {
        if (c == '0' || c == '1') {
            buffer.append(c);
            state = State.INT_LITERAL_BIN;
        } else if (c == '_') {
            buffer.append(c);
        } else {
            errorAndReset("Binary literal must contain at least one digit");
            initialState(c);
        }
    }

    private void floatLiteralDot(char c) {
        if ((c >= '0' && c <= '9') || c == '_') {
            buffer.append(c);
        } else if (c == 'E' || c == 'e') {
            buffer.append(c);
            state = State.FLOAT_LITERAL_EXPONENT_START;
        } else {
            addAndReset(TokenType.FLOAT_LITERAL);
            initialState(c);
        }
    }

    private void floatLiteralExponentStart(char c) {
        if (c == '+' || c == '-') {
            buffer.append(c);
            state = State.FLOAT_LITERAL_EXPONENT_NO_DIGITS;
        } else {
            errorAndReset("Expected + or - at the start of exponent");
            initialState(c);
        }
    }

    private void floatLiteralExponentNoDigits(char c) {
        if (c == '_') {
            buffer.append(c);
        } else if (c >= '0' && c <= '9') {
            buffer.append(c);
            state = State.FLOAT_LITERAL_EXPONENT;
        } else {
            errorAndReset("Exponent should have at least one digit");
            initialState(c);
        }
    }

    private void floatLiteralExponent(char c) {
        if ((c >= '0' && c <= '9') || c == '_') {
            buffer.append(c);
        } else {
            addAndReset(TokenType.FLOAT_LITERAL);
            initialState(c);
        }
    }

    private void slash(char c) {
        if (c == '*') {
            buffer.append(c);
            state = State.COMMENT_BLOCK_START;
        } else if (c == '/') {
            buffer.append(c);
            state = State.COMMENT_LINE;
        } else if (c == '=') {
            addEmptyAndReset(TokenType.SLASH_EQ);
        } else {
            addEmptyAndReset(TokenType.SLASH);
            initialState(c);
        }
    }

    private void onCommentBlockStart() {
        if (nestedCommentDepth == 0)
            outerCommentState = state;
        nestedCommentDepth++;
    }

    private void onCommentBlockEnd(TokenType type, boolean isNested) {
        if (isNested)
            nestedCommentDepth--;
        if (nestedCommentDepth == 0)
            addAndReset(type);
        else
            state = outerCommentState;
    }


    private void commentBlockStart(char c) {
        buffer.append(c);

        if (c == '*') {
            state = State.COMMENT_BLOCK_MAYBE_OUTER_DOC_START;
        } else if (c == '!') {
            state = State.COMMENT_BLOCK_INNER_DOC;
            onCommentBlockStart();
        } else {
            state = State.COMMENT_BLOCK;
            onCommentBlockStart();
        }
    }

    private void commentBlockMaybeOuterDocStart(char c) {
        buffer.append(c);

        if (c == '*') {
            state = State.COMMENT_BLOCK; /*** - too many asterisks */
            onCommentBlockStart();
        } else if (c == '/') {
            onCommentBlockEnd(TokenType.COMMENT, false); /**/
        } else {
            state = State.COMMENT_BLOCK_OUTER_DOC;
            onCommentBlockStart();
        }
    }

    private void commentBlock(char c, TokenType type) {
        buffer.append(c);

        if (c == '/' && buffer.charAt(buffer.length() - 2) == '*')
            onCommentBlockEnd(type, true);
        else if (c == '*' && buffer.charAt(buffer.length() - 2) == '/')
            state = State.COMMENT_BLOCK_START;
    }

    private void commentLineStart(char c) {
        if (c == '!') {
            buffer.append(c);
            state = State.COMMENT_LINE_INNER_DOC;
        } else if (c == '/') { ///
            buffer.append(c);
            state = State.COMMENT_LINE_MAYBE_OUTER_DOC_START;
        } else if (c == '\n') { //
            addAndReset(TokenType.COMMENT);
        } else {
            buffer.append(c);
            state = State.COMMENT_LINE;
        }
    }

    private void commentLineMaybeOuterDocStart(char c) {
        if (c == '/') { //// - too many slashes
            buffer.append(c);
            state = State.COMMENT_LINE;
        } else {
            state = State.COMMENT_LINE_OUTER_DOC;
            commentLine(c, TokenType.COMMENT_OUTER_DOC);
        }
    }

    private void commentLine(char c, TokenType type) {
        if (c == '\n') {
            addAndReset(type);
        } else {
            buffer.append(c);
        }
    }

    private void plus(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.PLUS_EQ);
        } else {
            addEmptyAndReset(TokenType.PLUS);
            initialState(c);
        }
    }

    private void minus(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.MINUS_EQ);
        } else if (c == '>') {
                addEmptyAndReset(TokenType.R_ARROW);
        } else {
            addEmptyAndReset(TokenType.MINUS);
            initialState(c);
        }
    }

    private void star(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.STAR_EQ);
        } else {
            addEmptyAndReset(TokenType.STAR);
            initialState(c);
        }
    }

    private void percent(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.PERCENT_EQ);
        } else {
            addEmptyAndReset(TokenType.PERCENT);
            initialState(c);
        }
    }

    private void caret(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.CARET_EQ);
        } else {
            addEmptyAndReset(TokenType.CARET);
            initialState(c);
        }
    }

    private void not(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.NE);
        } else {
            addEmptyAndReset(TokenType.NOT);
            initialState(c);
        }
    }

    private void and(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.AND_EQ);
        } else if (c == '&') {
            addEmptyAndReset(TokenType.AND_AND);
        } else {
            addEmptyAndReset(TokenType.AND);
            initialState(c);
        }
    }

    private void or(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.OR_EQ);
        } else if (c == '|') {
            addEmptyAndReset(TokenType.OR_OR);
        } else {
            addEmptyAndReset(TokenType.OR);
            initialState(c);
        }
    }

    private void lessThan(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.LE);
        } else if (c == '<') {
            state = State.SHL;
        } else {
            addEmptyAndReset(TokenType.LT);
            initialState(c);
        }
    }

    private void greaterThan(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.GE);
        } else if (c == '>') {
            state = State.SHR;
        } else {
            addEmptyAndReset(TokenType.GT);
            initialState(c);
        }
    }

    private void shiftLeft(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.SHL_EQ);
        } else {
            addEmptyAndReset(TokenType.SHL);
            initialState(c);
        }
    }

    private void shiftRight(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.SHR_EQ);
        } else {
            addEmptyAndReset(TokenType.SHR);
            initialState(c);
        }
    }

    private void equals(char c) {
        if (c == '=') {
            addEmptyAndReset(TokenType.EQ_EQ);
        } else if (c == '>') {
            addEmptyAndReset(TokenType.FAT_ARROW);
        } else {
            addEmptyAndReset(TokenType.EQ);
            initialState(c);
        }
    }

    private void dot(char c) {
        if (c == '.') {
            state = State.DOT_DOT;
        } else {
            addEmptyAndReset(TokenType.DOT);
            initialState(c);
        }
    }

    private void dotDot(char c) {
        if (c == '.') {
            addEmptyAndReset(TokenType.DOT_DOT_DOT);
        } else if (c == '=') {
            addEmptyAndReset(TokenType.DOT_DOT_EQ);
        } else {
            addEmptyAndReset(TokenType.DOT_DOT);
            initialState(c);
        }
    }

    private void colon(char c) {
        if (c == ':') {
            addEmptyAndReset(TokenType.PATH_SEPARATOR);
        } else {
            addEmptyAndReset(TokenType.COLON);
            initialState(c);
        }
    }
}
