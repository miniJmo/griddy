package com.company.target;

import com.company.parser.*;
import java.util.ArrayList;
import java.util.HashMap;
import com.company.Util;

/**
 * Griddy visitor for C targets.
 */
public class CVisitor extends GriddyDefaultVisitor {
    /** Throw error in case a base AST node is encountered. */
    @Override
    public Object visit(SimpleNode node, Object data){
        throw new RuntimeException("Encountered SimpleNode");
    }

    /** Root */
    @Override
    public Object visit(ASTStart node, Object data){
        var output = (StringBuilder) data;

        // Include C libraries that might be needed:
        output.append(
                        """
                        /* === Code generated by Griddy compiler === */
                        #include <stdio.h>
                        #include <stdlib.h>
                        #include <string.h>
                        
                        int main(int argc, char *argv[]){
                        struct Piece { char* name; unsigned int limit; unsigned int count; };
                        struct Piece empty_piece;
                        empty_piece.name = calloc(7, sizeof(char));
                        strcpy(empty_piece.name, " ");
                        
                        /*    SETUP    */
                        """
        );

        node.childrenAccept(this, data);

        return output.append("\nreturn 0;\n}\n");
    }

    /**
     * {@code output} print statement, which maps to C's {@code printf}.
     * */
    @Override
    public Object visit(ASTOutput node, Object data) {
        var output = (StringBuilder) data;
        var arg = node.jjtGetChild(0);
        String argType = GriddyTreeConstants.jjtNodeName[arg.getId()];
        Node assocNode = null;

        if (argType.equals("Ident")) {
            ArrayList<Node> prevAssign = Util.getAssignedInScope(node, arg.jjtGetValue().toString());
            if (prevAssign.isEmpty()) throw new RuntimeException("Identifier '" + arg.jjtGetValue() + "' unknown.");
            assocNode = prevAssign
                    .get(prevAssign.size() - 1)
                    .jjtGetChild(1);
            argType = GriddyTreeConstants.jjtNodeName[assocNode.getId()];
        }

        return switch (argType) {
            case "Integer", "Expr", "Boolean" -> {
                output.append("printf(\"%d\\n\", ");
                arg.jjtAccept(this, data);
                yield output.append(");\n");
            }
            case "String" -> {
                output.append("printf(\"%s\\n\", ");
                arg.jjtAccept(this, data);
                yield output.append(");\n");
            }
            case "Piece", "Access" -> {
                output.append("printf(\"%s\\n\", ");
                arg.jjtAccept(this, data);
                yield output.append(".name")
                        .append(");\n");
            }
            case "Board" -> {
                assert assocNode != null;
                @SuppressWarnings("unchecked")
                var boardSize = (ArrayList<Integer>)assocNode.jjtGetValue();

                output.append("printf(\"┌───\");\n")
                        .append("printf(\"┬───\");\n".repeat(Math.max(0, boardSize.get(0) - 1)))
                        .append("printf(\"┐\\n\");\n");

                for (int y = boardSize.get(1); y > 0 ; y--) {
                    for (int x = 0; x < boardSize.get(0); x++) {
                        output.append("printf(\"│ %c \", *");
                        arg.jjtAccept(this, data);
                        output.append("[").append(y - 1).append("][").append(x)
                                .append("]->name);\n");
                    }

                    output.append("printf(\"│ ")
                            .append(y)
                            .append("\\n\");\n");

                    if (y > 1) {
                        output.append("printf(\"├───\");\n")
                                .append(("printf(\"┼───\");\n").repeat(Math.max(0, boardSize.get(0) - 1)))
                                .append("printf(\"┤\\n\");\n");
                    } else {
                        output.append("printf(\"└───\");\n")
                                .append(("printf(\"┴───\");\n").repeat(Math.max(0, boardSize.get(0) - 1)))
                                .append("printf(\"┘\\n\");\n");
                    }
                }

                for (int x = 0; x < boardSize.get(0); x++)
                    output.append("printf(\"  ")
                            .append((char)('A' + x))
                            .append(" \");\n");

                yield output.append("printf(\"\\n\");\n");
            }
            default -> throw new RuntimeException("Can't echo value of unknown type: " + argType);
        };
    }

    @Override
    public Object visit(ASTGame node, Object data){
        // 1. /*  GAME    */
        // 2. int i = 5;
        // 3. do {
        // ...
        // n. } while (0 < i--);

        ((StringBuilder) data)
                .append("/*   GAME    */\n")
                .append("int i = 5;\n") // for testing while win condition is unimplemented.
                .append("do {\n");

        for (Node child : node.getChildren())
            child.jjtAccept(this, data);

        ((StringBuilder) data).append("\n} while (0 < i--);");

        return data;
    }

    public Object visit(ASTBoard node, Object data){
        return data;
    }

    /**
     * Variable assignment nodes.
     * <br>
     * Example: {@code my_var = 42}.
     */
    @Override
    public Object visit(ASTAssign node, Object data) {
        var output = (StringBuilder) data;

        Node identNode = node.jjtGetChild(0);
        Node valueNode = node.jjtGetChild(1);
        String ident = identNode.jjtGetValue().toString();
        Object value = valueNode.jjtGetValue();
        String valueType = GriddyTreeConstants.jjtNodeName[valueNode.getId()];

        // Generate code based on whether the identifier being assigned, has already been declared or not:
        if (Util.isDeclaredInScope(identNode, ident))
            return switch (valueType) {
                case "String" -> {
                    // 1. str_ptr = realloc(str_ptr, str_size);
                    // 2. strcpy(str_ptr, str_val);
                    identNode.jjtAccept(this, data);
                    output.append(" = realloc(");
                    identNode.jjtAccept(this, data);
                    output.append(", ")
                            .append(value.toString().length() + 1)
                            .append(");\n")
                            .append("strcpy(");
                    identNode.jjtAccept(this, data);
                    output.append(", ");
                    valueNode.jjtAccept(this, data);
                    yield output.append(");\n");
                }
                // Integer values 0 and >0 used in C boolean expressions instead of bool literals.
                case "Integer", "Boolean", "Expr" -> {
                    // 1. var_name = int_val;
                    identNode.jjtAccept(this, data);
                    output.append(" = ");
                    valueNode.jjtAccept(this, data);
                    yield output.append(";\n");
                }
                // NOTE: Board might not be re-assignable
                case "Board" -> output.append("/* Board declarations not yet implemented... */\n");
                default -> throw new RuntimeException("Encountered invalid value type in assignment: " + valueNode);
            };

        return switch (valueType) {
            // 1. char *str_ptr;
            // 2. str_ptr = calloc(str_size, sizeof(char));
            // 3. strcpy(str_ptr, str_val);
            case "String" -> {
                output.append("char *");
                identNode.jjtAccept(this, data);
                output.append(";\n");
                identNode.jjtAccept(this, data);
                output.append(" = calloc(")
                        .append(value.toString().length() + 1)
                        .append(", sizeof(char));\n")
                        .append("strcpy(");
                identNode.jjtAccept(this, data);
                output.append(", ");
                valueNode.jjtAccept(this, data);
                yield output.append(");\n");
            }

            // Integer values 0 and >0 used in C boolean expressions instead of bool literals.
            // 1. int var_name = int_value;
            case "Integer", "Boolean", "Expr" -> {
                output.append("int ");
                identNode.jjtAccept(this, data);
                output.append(" = ");
                valueNode.jjtAccept(this, data);
                yield output.append(";\n");
            }
            case "Empty" -> {
                valueNode.jjtAccept(this, data);
                identNode.jjtAccept(this, data);
                yield output.append(";\n");
            }
            // 1. struct Piece game_piece;
            // ?. game_piece.??? = ???;
            case "Piece" -> {
                @SuppressWarnings("unchecked")
                var pieceProps = (HashMap<ASTIdent, Node>) valueNode.jjtGetValue();

                output.append("struct Piece ");
                identNode.jjtAccept(this, data);
                output.append(";\n");

                pieceProps.forEach((k, v) -> {
                    identNode.jjtAccept(this, data);
                    output.append(".");
                    k.jjtAccept(this, data);
                    output.append(" = ");

                    if ("String".equals(GriddyTreeConstants.jjtNodeName[v.getId()])) {
                        output.append("calloc(")
                                .append(v.jjtGetValue().toString().length() + 1)
                                .append(", sizeof(char));\n")
                                .append("strcpy(");
                        identNode.jjtAccept(this, data);
                        output.append(".");
                        k.jjtAccept(this, data);
                        output.append(", ");
                        v.jjtAccept(this, data);
                        output.append(");\n");
                    } else {
                        v.jjtAccept(this, data);
                        output.append(";\n");
                    }
                });

                identNode.jjtAccept(this, data);
                yield output.append(".count = 0;\n");
            }
            case "Access" -> {
                output.append("struct Piece ");
                identNode.jjtAccept(this, data);
                output.append(";\n");
                identNode.jjtAccept(this, data);
                output.append(" = ");
                valueNode.jjtAccept(this, data);
                output.append(";\n");
                yield output.append(";\n");
            }
            // 1. struct Piece *board[height][width];
            case "Board" -> {
                @SuppressWarnings("unchecked")
                var boardDim = (ArrayList<Integer>) valueNode.jjtGetValue();

                output.append("struct Piece *");
                identNode.jjtAccept(this, data);
                output.append("[")
                        .append(boardDim.get(1))
                        .append("][")
                        .append(boardDim.get(0))
                        .append("];\n")
                        .append("for (int y = 0; y < ")
                        .append(boardDim.get(1))
                        .append("; y++)")
                        .append("for (int x = 0; x < ")
                        .append(boardDim.get(0))
                        .append("; x++)\n");
                identNode.jjtAccept(this, data);
                yield output.append("[y][x] = &empty_piece;\n");
            }
            default -> throw new RuntimeException("Encountered invalid value type in assignment: " + valueNode);
        };
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        var output = (StringBuilder) data;
        output.append("(");
        for (Node c : node.getChildren())
            c.jjtAccept(this, data);
        return output.append(")");
    }

    @Override
    public Object visit(ASTEmpty node, Object data) {
        var type = (String) node.jjtGetValue();

        return ((StringBuilder) data).append(switch (type) {
            case "number", "boolean" -> "int ";
            case "string" -> "char *";
            default -> throw new RuntimeException("Unknown type in empty assignment: '" + type + "'");
        });
    }

    @Override
    public Object visit(ASTOperator node, Object data) {
        return ((StringBuilder) data).append(node.jjtGetValue());
    }

    @Override
    public Object visit(ASTString node, Object data) {
        return ((StringBuilder) data)
                .append("\"")
                .append(node.jjtGetValue())
                .append("\"");
    }

    @Override
    public Object visit(ASTIdent node, Object data) {
        return ((StringBuilder) data).append(node.jjtGetValue());
    }

    @Override
    public Object visit(ASTInteger node, Object data) {
        return ((StringBuilder) data).append(node.jjtGetValue());
    }

    @Override
    public Object visit(ASTBoolean node, Object data) {
        return ((StringBuilder) data).append("true".equals(node.jjtGetValue()) ? 1 : 0);
    }

    /**
     * Generates:
     * <br>
     * {@code 1. some_board[y][x]}
     * @param node an access AST node
     * @param data String Builder
     * @return StringBuilder
     */
    public Object visit(ASTAccess node, Object data) {
        var out = (StringBuilder)data;

        out.append("*");
        node.jjtGetChild(1).jjtAccept(this, data);  //  board
        out.append("[");
        node.jjtGetChild(0).jjtGetChild(1).jjtAccept(this, data);   //  Y
        out.append("-1][");
        node.jjtGetChild(0).jjtGetChild(0).jjtAccept(this, data);   //  X
        return out.append("-1]");
    }

    @Override
    public Object visit(ASTPlace node, Object data) {
        var out = (StringBuilder) data;
        var piece = node.jjtGetChild(0);
        var pos = node.jjtGetChild(2);

        out.append("if (");
        piece.jjtAccept(this, data);
        out.append(".count < ");
        piece.jjtAccept(this, data);
        out.append(".limit) {\n");
        node.jjtGetChild(1).jjtAccept(this, data);  //  board
        out.append("[");
        pos.jjtGetChild(1).jjtAccept(this, data);   //  Y
        out.append("-1][");
        pos.jjtGetChild(0).jjtAccept(this, data);   //  X
        out.append("-1] = &");
        piece.jjtAccept(this, data);
        out.append(";\n");
        piece.jjtAccept(this, data);
        out.append(".count++;\n}\n");

        return data;
    }
}
