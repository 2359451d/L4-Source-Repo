package ast.visitor.impl;

import ast.visitor.PascalBaseVisitor;
import ast.visitor.PascalParser;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.apache.commons.lang3.math.NumberUtils;
import runtime.RunTimeLibFactory;
import type.*;
import type.primitive.Integer32;
import util.ErrorMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PascalCheckerVisitor extends PascalBaseVisitor<TypeDescriptor> {


    // Contextual errors

    private int errorCount = 0;

    private CommonTokenStream tokens;

    private SymbolTable<TypeDescriptor> symbolTable = new SymbolTable<>();

    // Constructor

    public PascalCheckerVisitor(CommonTokenStream toks) {
        tokens = toks;
    }

    private void reportError(ParserRuleContext ctx, String message) {
        // Print an error message relating to the given
        // part of the AST.
        Interval interval = ctx.getSourceInterval();
        Token start = tokens.get(interval.a);
        Token finish = tokens.get(interval.b);
        int startLine = start.getLine();
        int startCol = start.getCharPositionInLine();
        int finishLine = finish.getLine();
        int finishCol = finish.getCharPositionInLine();
        System.err.println(startLine + ":" + startCol + "-" +
                finishLine + ":" + finishCol
                + " " + message);
        errorCount++;
    }

    /**
     * Report error in a formatted way
     *
     * @param ctx
     * @param message
     * @param args
     */
    private void reportError(ParserRuleContext ctx, String message, Object... args
    ) {
        // Print an error message relating to the given
        // part of the AST.
        Interval interval = ctx.getSourceInterval();
        Token start = tokens.get(interval.a);
        Token finish = tokens.get(interval.b);
        int startLine = start.getLine();
        int startCol = start.getCharPositionInLine();
        int finishLine = finish.getLine();
        int finishCol = finish.getCharPositionInLine();
        System.err.println(startLine + ":" + startCol + "-" +
                finishLine + ":" + finishCol
                + " " + String.format(message, args));
        errorCount++;
    }

    public int getNumberOfContextualErrors() {
        // Return the total number of errors so far detected.
        return errorCount;
    }

    private Integer32 getMaxInt(ParserRuleContext ctx) {
        TypeDescriptor maxint = retrieve("maxint", ctx);
        if (maxint instanceof Integer32) {
            return (Integer32) maxint;
        }
        return null;
    }

    private void predefine() {
        // Add predefined procedures to the type table.
        //BuiltInUtils.fillTable(symbolTable);
        symbolTable.put("maxint", new Integer32());
        RunTimeLibFactory.fillTable(symbolTable);
    }

    private void define(String id, TypeDescriptor type,
                        ParserRuleContext decl) {
        // Add id with its type to the type table, checking
        // that id is not already declared in the same scope.
        // IGNORE CASE
        boolean ok = symbolTable.put(id.toLowerCase(), type);
        if (!ok)
            reportError(decl, id + " is redeclared");
    }

    private TypeDescriptor retrieve(String id, ParserRuleContext occ) {
        // Retrieve id's type from the type table.
        // Case insensitive
        TypeDescriptor type = symbolTable.get(id.toLowerCase());
        if (type == null) {
            reportError(occ, id + " is undeclared");
            return Type.UNDEFINED_TYPE;
        } else
            return type;
    }

    @Override
    public TypeDescriptor visitProgram(PascalParser.ProgramContext ctx) {
        predefine();
        return super.visitProgram(ctx);
    }

    @Override
    public TypeDescriptor visitBlock(PascalParser.BlockContext ctx) {
        System.out.println("Block Starts*************************");
        symbolTable.displayCurrentScope();
        visitChildren(ctx);
        System.out.println("Block Ends*************************");
        return null;
    }

    @Override
    public TypeDescriptor visitIdentifier(PascalParser.IdentifierContext ctx) {
        //skip if the parent node is ProgramHeading, do not process the ProgramHeading ID
        ParserRuleContext parent = ctx.getParent();
        if (parent instanceof PascalParser.ProgramHeadingContext) return null;
        return retrieve(ctx.getText(), ctx);
    }

    @Override
    public TypeDescriptor visitIdentifierList(PascalParser.IdentifierListContext ctx) {
        return super.visitIdentifierList(ctx);
    }

    @Override
    public TypeDescriptor visitVariableDeclaration(PascalParser.VariableDeclarationContext ctx) {
        System.out.println("Variable Decl Starts*************");
        List<PascalParser.IdentifierContext> identifierContextList = ctx.identifierList().identifier();
        for (PascalParser.IdentifierContext identifierContext : identifierContextList) {
            String id = identifierContext.IDENT().getText();
            TypeDescriptor type = visit(ctx.type_());
            define(id, type, ctx);
        }
        symbolTable.displayCurrentScope();
        System.out.println("Variable Decl ends\n*************");
        return null;
    }

    /**
     * Constant definition part, inserted into the symbol table
     * Type checking whether right operand is allowed constant
     * There are 3 builtin constant (could be used without prior definition)
     * - false, true, maxInt
     * <p>
     * Remark: Error is reported in "constant" node, this node only
     * insert a new entry into the symbol table, if const type is valid
     * </p>
     *
     * <p>
     * constantDefinition
     * : identifier EQUAL constant
     * ;
     * constant
     * : unsignedNumber #unsignedNumberConst
     * | sign unsignedNumber #signedNumberConst
     * | identifier #constantIdentifier
     * | sign identifier #constantSignedIdentifier
     * | string #stringConst
     * | constantChr #chrConst // chr(int) , ordinal func
     * | TRUE #trueConst
     * | FALSE #falseConst
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitConstantDefinition(PascalParser.ConstantDefinitionContext ctx) {
        System.out.println("*************Define new Const");
        System.out.println("ctx.getText() = " + ctx.getText());

        String id = ctx.identifier().getText();
        TypeDescriptor constantType = visit(ctx.constant());
        if (!constantType.equiv(Type.INVALID_CONSTANT_TYPE)) {
            define(id, constantType, ctx);
        }
        return null;
    }

    /**
     * Legal constant type (unsigned): int, real
     *
     * constant
     *      * : unsignedNumber #unsignedNumberConst
     *
     * unsignedNumber
     *    : unsignedInteger
     *    | unsignedReal
     *    ;
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitUnsignedNumberConst(PascalParser.UnsignedNumberConstContext ctx) {
        switch (ctx.unsignedNumber().type.getType()) {
            // check whether unsignedNumber(int) is less than maxint
            case PascalParser.NUM_INT:
                String unsignedInt = ctx.unsignedNumber().getText();
                int unsignedIntValue = NumberUtils.toInt(unsignedInt, 0);
                // overflows, 0 value derived from utils function
                if (unsignedIntValue == 0 && unsignedInt.length() != 1) {
                    //reportError(ctx,"Illegal constant definition [%s] with right operand [%s]" +
                    //        "which must be between [%d] and [%d]",
                    //
                    //        );
                    return ErrorType.INVALID_COSNTANT_TYPE;
                }
                break;
            case PascalParser.NUM_REAL:
                return Type.REAL;
        }
        return null;
    }

    /**
     * Check whether right operand(int) is in the range of (-maxint, maxint)
     * No need to check whether sign is applicable on right operand
     * As right operand must be real or integer
     * <p>
     * sign unsignedNumber #signedNumberConst
     * <p>
     * unsignedNumber
     * : unsignedInteger
     * | unsignedReal
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitSignedNumberConst(PascalParser.SignedNumberConstContext ctx) {
        //String monadicOperator = ctx.sign().getText();
        TypeDescriptor unsignedNumber = visit(ctx.unsignedNumber());
        // Only check, if right operand is integer
        if (unsignedNumber.equiv(Type.INTEGER)) {
            Double rightOperand = Double.valueOf(ctx.unsignedNumber().getText());
            Integer32 maxInt = getMaxInt(ctx);
            Integer maxIntMaxValue = maxInt.getMaxValue();
            Integer maxIntMinValue = maxInt.getMinValue();

            if (rightOperand <= maxIntMaxValue && rightOperand >= maxIntMinValue) return Type.INTEGER;
            reportError(ctx, "Illegal constant definition [%s] with right operand [%s]" +
                            "which must be between [%d] and [%d]",
                    ctx.getParent().getText(), rightOperand, maxIntMinValue, maxIntMaxValue);
            return Type.INVALID_CONSTANT_TYPE;

            //switch (monadicOperator) {
            //    case "+":
            //        if (rightOperand <= maxIntMaxValue) return Type.INTEGER;
            //        else {
            //            reportError(ctx, "Illegal constant definition [%s] with right operand [%s]" +
            //                            "which must be between [%d] and [%d]",
            //                    ctx.getParent().getText(), rightOperand, maxIntMinValue, maxIntMaxValue);
            //            return Type.INVALID_CONSTANT_TYPE;
            //        }
            //        break;
            //    case "-":
            //        if (rightOperand > min)
            //}
        }
        return Type.REAL;
    }

    @Override
    public TypeDescriptor visitConstantIdentifier(PascalParser.ConstantIdentifierContext ctx) {
        return super.visitConstantIdentifier(ctx);
    }

    @Override
    public TypeDescriptor visitConstantSignedIdentifier(PascalParser.ConstantSignedIdentifierContext ctx) {
        return super.visitConstantSignedIdentifier(ctx);
    }

    @Override
    public TypeDescriptor visitStringConst(PascalParser.StringConstContext ctx) {
        return super.visitStringConst(ctx);
    }

    @Override
    public TypeDescriptor visitChrConst(PascalParser.ChrConstContext ctx) {
        return super.visitChrConst(ctx);
    }

    @Override
    public TypeDescriptor visitBoolConst(PascalParser.BoolConstContext ctx) {
        return super.visitBoolConst(ctx);
    }

    //@Override
    //public Type visitTrueConst(PascalParser.TrueConstContext ctx) {
    //    Type visit = visitChildren(ctx);
    //    Type visit1 = visit(ctx.TRUE());
    //    System.out.println("visit1 = " + visit1);
    //    System.out.println("visit = " + visit);
    //    return super.visitTrueConst(ctx);
    //}
    //
    //@Override
    //public Type visitFalseConst(PascalParser.FalseConstContext ctx) {
    //    return super.visitFalseConst(ctx);
    //}

    ///**
    // * constant
    // *    : unsignedNumber
    // *    | sign unsignedNumber
    // *    | identifier
    // *    | sign identifier
    // *    | string
    // *    | constantChr // chr(int) , ordinal func
    // *    | TRUE
    // *    | FALSE
    // *    ;
    // *
    // * @param ctx
    // * @return
    // */
    //@Override
    //public Type visitConstant(PascalParser.ConstantContext ctx) {
    //    //System.out.println("ctx = " + ctx.getText());
    //    ////List<ParseTree> children = ctx.children;
    //    ////children.forEach(each->{
    //    ////    System.out.println("each.getClass() = " + each.getClass());
    //    ////});
    //    //return null;
    //}

    @Override
    public TypeDescriptor visitPrimitiveType(PascalParser.PrimitiveTypeContext ctx) {
        switch (ctx.primitiveType.getType()) {
            case PascalParser.INTEGER:
                return Type.INTEGER;
            case PascalParser.STRING:
                return Type.STRING_LITERAL;
            case PascalParser.CHAR:
                return Type.CHARACTER;
            case PascalParser.BOOLEAN:
                return Type.BOOLEAN;
            case PascalParser.REAL:
                return Type.REAL;
        }
        return super.visitPrimitiveType(ctx);
    }

    @Override
    public TypeDescriptor visitFileType(PascalParser.FileTypeContext ctx) {
        TypeDescriptor type = visit(ctx.type_());

        //return super.visitFileType(ctx);
        return new File(type);
    }

    private void checkAcceptableType(Set<String> acceptableTypes, Signature signature,
                                     ParserRuleContext ctx) {
        System.out.println("=======checkAcceptableTypes starts=========");
        System.out.println("acceptableTypes = " + acceptableTypes);
        System.out.println("actualSignature given = " + signature);
        List<String> paramList = signature.getParamList();
        for (String param : paramList) {
            //System.out.println("current param = " + param);
            if (!acceptableTypes.contains(param)) {
                reportError(ctx, "type: " + param
                        + " is not supported");
            }
        }
        System.out.println("=======checkAcceptableTypes ends=========");
    }

    private void checkAcceptableSignature(SignatureSet signatureSet, SignatureSet signature,
                                          ParserRuleContext ctx) {
        System.out.println("=======checkAcceptableSignature starts=========");
        System.out.println("actualSignature given = " + signature);
        Set<String> typeOrderToBeChecked = signatureSet.getTypeOrderToBeChecked();
        System.out.println("typeOrderToBeChecked = " + typeOrderToBeChecked);
        Set<Signature> acceptableSignatures = signatureSet.getAcceptableSignatures();
        System.out.println("acceptableSignatures = " + acceptableSignatures);

        ArrayList<Signature> actualSignatures = new ArrayList<>(signature.getAcceptableSignatures());
        Signature actualSignature = actualSignatures.get(0);
        List<String> paramList = actualSignature.getParamList();
        System.out.println("paramList = " + paramList);

        // trim the signature (as there are variable number of params)
        List<String> trimmedParamList = paramList.stream().distinct().collect(Collectors.toList());
        Signature trimmedSignature = new Signature(trimmedParamList);
        //System.out.println("trimmedParamList = " + trimmedParamList);
        System.out.println("trimmedSignature = " + trimmedSignature);

        if (!acceptableSignatures.contains(trimmedSignature)) {
            Set<String> acceptableTypes = signatureSet.getAcceptableTypes();
            System.out.println("acceptableTypes = " + acceptableTypes);
            Set<String> coveredTypes = signature.getAcceptableTypes();
            System.out.println("acceptableTypes1 = " + coveredTypes);
            boolean orderCheck = false;
            for (String s : coveredTypes) {
                if (acceptableTypes.contains(s)) {
                    orderCheck = true;
                    break;
                }
            }

            if (orderCheck) {
                boolean notDeclared = true;
                for (int i = 0; i < trimmedParamList.size(); i++) {
                    String actualParam = trimmedParamList.get(i);
                    for (Signature acceptableSignature : acceptableSignatures) {
                        List<String> acceptableSignatureParamList = acceptableSignature.getParamList();
                        if (typeOrderToBeChecked.contains(actualParam) && !acceptableSignatureParamList.get(i).equals(actualParam)) {
                            reportError(ctx, "signature " + signature
                                    + " is not supported");
                        }
                        //} else if (!typeOrderToBeChecked.contains(actualParam)){
                        //    if (trimmedParamList.size() <= acceptableSignatureParamList.size()) {
                        //
                        //    }
                        //}
                    }
                }
            } else {
                for (String coveredType : signature.getAcceptableTypes()) {
                    if (!acceptableTypes.contains(coveredType))
                        reportError(ctx, "signature " + signature + " is not supported");
                }
            }
        }


        // further checking, specifically for builtin overloading proc/func
        //if (!acceptableSignatures.contains(signature)) {
        //    for (int i = 0; i < paramList.size(); i++) {
        //        Type actualParam = paramList.get(i);
        //        for (Signature acceptableSignature : acceptableSignatures) {
        //            List<Type> acceptableSignatureParamList = acceptableSignature.getParamList();
        //            Set<Type> acceptableTypes = acceptableSignature.getAcceptableTypes();
        //            if (typeOrderToBeChecked.contains(actualParam.getClass().getName())) {
        //                System.out.println("TO be checked");
        //                if (!acceptableSignatureParamList.get(i).equiv(actualParam)) {
        //                    System.out.println("actualParam = " + actualParam);
        //                    System.out.println("acceptableSignatureParamList.get(i) = " + acceptableSignatureParamList.get(i));
        //                    reportError("signature " + signature
        //                            + " is not supported", ctx);
        //                }
        //            } else if (!acceptableTypes.contains(actualParam)){
        //                reportError("type " + actualParam +" in "+ "signature " + signature
        //                        + " is not supported", ctx);
        //            }
        //        }
        //    }
        //for (Type actualParam : paramList) {
        //}
        //}

        System.out.println("=======checkAcceptableSignature ends=========");

    }

    /**
     * formalParameterSection
     * : parameterGroup  #noLabelParam
     * | VAR parameterGroup #varLabelParam
     * | FUNCTION parameterGroup #funcParam
     * | PROCEDURE parameterGroup #procParam
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitNoLabelParam(PascalParser.NoLabelParamContext ctx) {
        TypeDescriptor type = visit(ctx.parameterGroup().typeIdentifier());
        List<PascalParser.IdentifierContext> identifiers = ctx.parameterGroup().identifierList().identifier();
        for (PascalParser.IdentifierContext identifier : identifiers) {
            // for each group, define the corresponding formal parameter with null label
            // (x,y,...:Type)
            System.out.println("Defin no label Param!!!!!!!!!!!!!");
            define(identifier.getText(), new FormalParam(type, null), ctx);
        }
        return null;
    }

    /**
     * formalParameterSection
     * : parameterGroup  #noLabelParam
     * | VAR parameterGroup #varLabelParam
     * | FUNCTION parameterGroup #funcParam
     * | PROCEDURE parameterGroup #procParam
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitVarLabelParam(PascalParser.VarLabelParamContext ctx) {
        TypeDescriptor type = visit(ctx.parameterGroup().typeIdentifier());
        List<PascalParser.IdentifierContext> identifiers = ctx.parameterGroup().identifierList().identifier();
        for (PascalParser.IdentifierContext identifier : identifiers) {
            // for each group, define the corresponding formal parameter with var label
            // (var x,y,...:Type)
            System.out.println("Defin var label Param!!!!!!!!!!!!!");
            define(identifier.getText(), new FormalParam(type, "var"), ctx);
        }
        return null;
    }

    /**
     * procedureDeclaration
     * : PROCEDURE identifier (formalParameterList)? SEMI block
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public Type visitProcedureDeclaration(PascalParser.ProcedureDeclarationContext ctx) {
        String id = ctx.identifier().getText().toLowerCase();
        ArrayList<TypeDescriptor> params = new ArrayList<>();

        System.out.println("PROC DECL Starts*************************");
        symbolTable.enterLocalScope();

        // if the procedure has formal parameters
        if (ctx.formalParameterList() != null) {
            List<PascalParser.FormalParameterSectionContext> formalParameterSectionList = ctx.formalParameterList().formalParameterSection();
            for (PascalParser.FormalParameterSectionContext paramSection : formalParameterSectionList) {
                // define parameter group in current scope of type Param(Type,String:label)
                visit(paramSection);
            }
            // all formal params set up
            Map<String, TypeDescriptor> allParams = symbolTable.getAllVarInCurrentScope();
            allParams.forEach((k, v) -> params.add(v));
        }
        define(id, new Procedure(params), ctx);
        System.out.println("Define Proc signature");
        symbolTable.displayCurrentScope();
        System.out.println("id = " + id);
        System.out.println("symbolTable.get(id) = " + symbolTable.get(id));
        visit(ctx.block()); // scope & type checking in current proc scope
        symbolTable.exitLocalScope(); // back to last scope
        define(id, new Procedure(params), ctx);
        System.out.println("PROC DECL ENDS*************************");
        symbolTable.displayCurrentScope();
        return null;
    }

    /**
     * Check whether a Function has result assignment or not
     * Report errors if the result assignment statement is missing
     *
     * @param ctx - function rule context
     * @return boolean - true if function has result assignment, otherwise return false
     */
    private boolean functionHasResultAssignment(PascalParser.FunctionDeclarationContext ctx) {
        List<PascalParser.StatementContext> statementContextList = ctx.block().compoundStatement().statements().statement();
        boolean functionHasResultAssignment = false;
        for (PascalParser.StatementContext statementContext : statementContextList) {
            if (visit(statementContext) instanceof Function) {
                functionHasResultAssignment = true;
            }
        }
        return functionHasResultAssignment;
    }

    /**
     * functionDeclaration
     * : FUNCTION identifier (formalParameterList)? COLON resultType SEMI block
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitFunctionDeclaration(PascalParser.FunctionDeclarationContext ctx) {
        String id = ctx.identifier().getText();
        TypeDescriptor resultType = visit(ctx.resultType());
        ArrayList<TypeDescriptor> params = new ArrayList<>();

        System.out.println("Func DECL Starts*************************");
        symbolTable.enterLocalScope();

        // if the procedure has formal parameters
        if (ctx.formalParameterList() != null) {
            List<PascalParser.FormalParameterSectionContext> formalParameterSectionList = ctx.formalParameterList().formalParameterSection();
            for (PascalParser.FormalParameterSectionContext paramSection : formalParameterSectionList) {
                // define parameter group in current scope of type Param(Type,String:label)
                visit(paramSection);
            }
            // all formal params set up
            Map<String, TypeDescriptor> allParams = symbolTable.getAllVarInCurrentScope();
            allParams.forEach((k, v) -> params.add(v));
        }
        Function function = new Function(params, resultType);
        define(id, function, ctx);
        System.out.println("Define Func signature");
        symbolTable.displayCurrentScope();
        System.out.println("id = " + id);
        System.out.println("symbolTable.get(id) = " + symbolTable.get(id));

        if (!functionHasResultAssignment(ctx)) {
            reportError(ctx, "Missing result assignment in Function: %s", function);
        }

        symbolTable.exitLocalScope(); // back to last scope
        define(id, new Function(params, resultType), ctx);
        System.out.println("FUNC DECL ENDS*************************");
        symbolTable.displayCurrentScope();

        return null;
    }

    /**
     * Check single formal parameter and actual parameter
     * Whether these are the same or not
     *
     * @param formalParam
     * @param actualParam
     * @return
     */
    private boolean checkFormalAndActual(FormalParam formalParam, ActualParam actualParam) {
        if (formalParam.getLabel() == null) return formalParam.getType().equiv(actualParam.getType());
        if (!(formalParam.getLabel().equals(actualParam.getLabel()))) return false;
        return formalParam.getType().equiv(actualParam.getType());
    }

    /**
     * @param signature
     * @param actualParameters
     * @return BooleanMessage - contains {boolean flag, List<String> messageSequence}
     */
    private ErrorMessage checkSignature(Type signature, List<PascalParser.ActualParameterContext> actualParameters) {
        ErrorMessage errorMessage = new ErrorMessage();
        // default - set the flag to false, i.e. exist errors to report
        //errorMessage.setFlag(false);
        List<TypeDescriptor> formalParameters = null;
        if (signature instanceof Procedure) {
            formalParameters = ((Procedure) signature).getFormalParams();
        }
        if (signature instanceof Function) {
            formalParameters = ((Function) signature).getFormalParams();
        }
        //List<Type> formalParameters = signature.getFormalParams();

        // if actual parameters length cannot match the formal parameters, directly return the error message
        if (actualParameters.size() < formalParameters.size()) {
            errorMessage.setMessageSequence(new StringBuilder(String.format(
                    "The number of actual parameters cannot match the signature! " +
                            "Actual: %d, Expected: %d",
                    actualParameters.size(),
                    formalParameters.size())));
        } else {
            //AtomicInteger count = new AtomicInteger(0);
            //List<String> messageSequence = new ArrayList<>();
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < formalParameters.size(); i++) {
                TypeDescriptor formal = formalParameters.get(i);
                TypeDescriptor actual = visit(actualParameters.get(i));
                ActualParam _actual = (ActualParam) actual;
                FormalParam _formal = (FormalParam) formal;
                if (!checkFormalAndActual(_formal, _actual)) {
                    if (stringBuilder.length() == 0) {
                        stringBuilder.append("Actual parameter cannot match the formal parameter!");
                    }
                    stringBuilder.append(String.format(
                            "\n- Pos[%d]\n" +
                                    " Actual: %s, Expected: %s",
                            i, _actual, _formal
                    ));
                }
            }
            //formalParameters.forEach(
            //        formal -> {
            //
            //        }
            //);
            errorMessage.setMessageSequence(stringBuilder);
        }
        return errorMessage;
    }

    @Override
    public Type visitProcedureStatement(PascalParser.ProcedureStatementContext ctx) {
        System.out.println("*******************PROC CALL");
        String id = ctx.identifier().getText();
        System.out.println("id = " + id);
        TypeDescriptor signature = retrieve(id, ctx);
        symbolTable.displayCurrentScope();
        //System.out.println("retrieve = " + retrieve);

        // Only report while proc id is defined but is used with other type
        if (!(signature.equiv(Type.UNDEFINED_TYPE)) && !(signature instanceof Procedure)) {
            reportError(ctx, id + " is not a procedure");
            return Type.INVALID_TYPE;
        } else {

            System.out.println("Signature = " + signature);
            Procedure _signature = (Procedure) signature;
            //check signatures
            List<PascalParser.ActualParameterContext> actualParameterContextList = ctx.parameterList().actualParameter();
            ErrorMessage errorMessage = checkSignature(_signature, actualParameterContextList);
            System.out.println("booleanMessage = " + errorMessage);
            if (errorMessage.hasErrors()) {
                reportError(ctx, errorMessage.getMessageSequence());
            }

            //// type checking
            //SignatureSet signatureSet = (SignatureSet) signature;
            //Set<String> acceptableTypes = signatureSet.getAcceptableTypes();
            ////System.out.println("acceptableTypes = " + acceptableTypes);
            ////Type expectedParamSeq = type.domain;
            //
            //// check actual_params == definition in the symbol table
            //SignatureSet actualSignatureSet = (SignatureSet) visit(ctx.parameterList());
            ////System.out.println("actualSignature = " + actualSignature);
            //
            //// first checking whether the actual params given are supported or not
            ////checkAcceptableType(acceptableTypes, actualSignature, ctx);
            //// then check whether there are limitations on params order & number or not
            //checkAcceptableSignature(signatureSet, actualSignatureSet, ctx);
            //
            //
            ////Set<String> set = new HashSet<>();
            ////set.add(s.getClass().getName());
            ////System.out.println("set = " + set);
            ////System.out.println(set.contains(Str.class.getName()));
            //
            ////Type actualParamSeq = visit(ctx.parameterList());
            ////if (!actualParamSeq.equiv(expectedParamSeq)) {
            ////    reportError("type is " + actualParamSeq
            ////            + ", should be " + expectedParamSeq, ctx);
            ////}
        }
        System.out.println("*******************PROC CALL ENDS");
        return null;
    }

    /**
     * Function Call
     * functionDesignator
     * : identifier LPAREN parameterList RPAREN
     * ;
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitFunctionDesignator(PascalParser.FunctionDesignatorContext ctx) {
        System.out.println("*******************FUNC CALL");
        String id = ctx.identifier().getText();
        System.out.println("id = " + id);
        TypeDescriptor signature = retrieve(id, ctx);
        symbolTable.displayCurrentScope();
        //System.out.println("retrieve = " + retrieve);

        // Only report while function id is defined but is used with other type
        if (!(signature.equiv(Type.UNDEFINED_TYPE)) && !(signature instanceof Function)) {
            reportError(ctx, id + " is not a function");
            return Type.INVALID_TYPE;
        } else {
            System.out.println("Signature = " + signature);
            Function _signature = (Function) signature;
            //check signatures
            List<PascalParser.ActualParameterContext> actualParameterContextList = ctx.parameterList().actualParameter();
            ErrorMessage errorMessage = checkSignature(_signature, actualParameterContextList);
            System.out.println("booleanMessage = " + errorMessage);
            if (errorMessage.hasErrors()) {
                reportError(ctx, errorMessage.getMessageSequence());
            }

            //// type checking
            //SignatureSet signatureSet = (SignatureSet) signature;
            //Set<String> acceptableTypes = signatureSet.getAcceptableTypes();
            ////System.out.println("acceptableTypes = " + acceptableTypes);
            ////Type expectedParamSeq = type.domain;
            //
            //// check actual_params == definition in the symbol table
            //SignatureSet actualSignatureSet = (SignatureSet) visit(ctx.parameterList());
            ////System.out.println("actualSignature = " + actualSignature);
            //
            //// first checking whether the actual params given are supported or not
            ////checkAcceptableType(acceptableTypes, actualSignature, ctx);
            //// then check whether there are limitations on params order & number or not
            //checkAcceptableSignature(signatureSet, actualSignatureSet, ctx);
            //
            //
            ////Set<String> set = new HashSet<>();
            ////set.add(s.getClass().getName());
            ////System.out.println("set = " + set);
            ////System.out.println(set.contains(Str.class.getName()));
            //
            ////Type actualParamSeq = visit(ctx.parameterList());
            ////if (!actualParamSeq.equiv(expectedParamSeq)) {
            ////    reportError("type is " + actualParamSeq
            ////            + ", should be " + expectedParamSeq, ctx);
            ////}
        }
        System.out.println("*******************Function CALL ENDS");
        return ((Function) signature).getResultType();
    }

    /**
     * parameterList
     * : actualParameter (COMMA actualParameter)*
     * ;
     * <p>
     * Used in FunctionDesignator(Func call) & ProcedureStatement(Proc Call)
     * Detailed processing is handled to the parent node Func/Proc call
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitParameterList(PascalParser.ParameterListContext ctx) {
        return visitChildren(ctx);

        //ArrayList<Type> types = new ArrayList<>();
        //List<PascalParser.ActualParameterContext> actualParameters = ctx.actualParameter();
        //for (PascalParser.ActualParameterContext actualParameter : actualParameters) {
        //    Type type = visit(actualParameter);
        //    types.add(type);
        //}
        //return new Sequence(types);

        //List<String> paramList = new ArrayList<>();
        //Set<String> typeSets = new HashSet<>();
        //List<PascalParser.ActualParameterContext> actualParameters = ctx.actualParameter();
        //for (PascalParser.ActualParameterContext actualParameter : actualParameters) {
        //    Type type = visit(actualParameter);
        //    paramList.add(type.toString());
        //    typeSets.add(type.getClass().getName());
        //}
        //Signature signature = new Signature(paramList);
        //HashSet<Signature> signatures = new HashSet<>();
        //signatures.add(signature);
        //return new SignatureSet(signatures, typeSets);
    }

    /**
     * actualParameter
     * : expression parameterwidth*
     * ;
     * expression
     * : simpleExpression (relationaloperator e2=expression)?
     * ;
     * simpleExpression
     * : term (additiveoperator simpleExpression)?
     * ;
     * <p>
     * term
     * : signedFactor (multiplicativeoperator term)?
     * ;
     * <p>
     * signedFactor
     * : monadicOp=(PLUS | MINUS)? factor
     * ;
     * factor
     * : variable   # factorVar
     * | LPAREN expression RPAREN #factorExpr
     * | functionDesignator #factorFuncDesignator
     * | unsignedConstant #factorUnConst
     * | set_ #factorSet
     * | NOT factor #notFactor
     * | bool_ #factorBool
     * ;
     * <p>
     * variable
     * : (AT identifier | identifier) (LBRACK expression (COMMA expression)* RBRACK | LBRACK2 expression (COMMA expression)* RBRACK2 | DOT identifier | POINTER)*
     * ;
     * <p>
     * ISO 7185
     * actual-parameter = expression | variable-access |
     * procedure-identifier | function-identifier
     * Note: parameterwidth not in standard Pascal
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitActualParameter(PascalParser.ActualParameterContext ctx) {
        TypeDescriptor actualParamType = visit(ctx.expression());
        String label = null;
        Token relationalOperator = ctx.expression().relationalOperator;
        Token additiveOperator = ctx.expression().simpleExpression().additiveOperator;
        Token multiplicativeOperator = ctx.expression().simpleExpression().term().multiplicativeOperator;
        Token monadicOperator = ctx.expression().simpleExpression().term().signedFactor().monadicOperator;

        // if no operator involved
        if (relationalOperator == null && additiveOperator == null &&
                multiplicativeOperator == null && monadicOperator == null) {
            PascalParser.FactorContext factorContext = ctx.expression().simpleExpression().term().signedFactor().factor();
            if (factorContext instanceof PascalParser.FactorVarContext) {
                // variable as actual parameter
                // TODO: FUNC, PROC as parameter not implemented
                label = "var";
            }
        }
        return new ActualParam(actualParamType, label);
    }

    /**
     * whileStatement
     * : WHILE expression DO statement
     * ;
     * Expression must be boolean
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitWhileStatement(PascalParser.WhileStatementContext ctx) {
        TypeDescriptor conditionType = visit(ctx.expression());
        if (!conditionType.equiv(Type.BOOLEAN)) {
            reportError(ctx, "Condition 【%s: %s】 of while statement must be boolean",
                    ctx.expression().getText(), conditionType);
        }
        visit(ctx.statement());
        return null;
    }

    /**
     * repeatStatement
     * : REPEAT statements UNTIL expression
     * ;
     * Expression must be boolean
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitRepeatStatement(PascalParser.RepeatStatementContext ctx) {
        TypeDescriptor exType = visit(ctx.expression());
        if (!exType.equiv(Type.BOOLEAN)) {
            reportError(ctx, "Expression 【%s: %s】 of" +
                            " Repeat Statement must be boolean",
                    ctx.expression().getText(), exType);
        }
        visit(ctx.statements());
        return null;
    }

    /**
     * forStatement
     * : FOR identifier ASSIGN forList DO statement
     * ;
     * <p>
     * forList
     * : initialValue (TO | DOWNTO) finalValue
     * ;
     * <p>
     * initialValue
     * : expression
     * ;
     * finalValue
     * : expression
     * ;
     * The type of control var must be ordinal type
     * The initialValue & finalValue must yield values
     * of the same ordinal type as the control var
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitForStatement(PascalParser.ForStatementContext ctx) {
        String forHeader = concatenateChildrenText(ctx, 0, 4);
        TypeDescriptor controlType = retrieve(ctx.identifier().getText(), ctx);
        if (!isOrdinalType(controlType)) {
            // form the text of FOR header
            reportError(ctx, "Control variable 【%s: %s】 of For " +
                            "statement 【%s】 must be Ordinal",
                    ctx.identifier().getText(), controlType.toString(), forHeader);
        } else {
            // check initialValue
            TypeDescriptor initType = visit(ctx.forList().initialValue());
            TypeDescriptor finalType = visit(ctx.forList().finalValue());
            if (!initType.equiv(controlType)) {
                reportError(ctx, "Initial Expression 【%s: %s】 " +
                                "of For Statement 【%s】\n " +
                                "must be the same ordinal type as control variable 【%s: %s】",
                        ctx.forList().initialValue().getText(),
                        initType.toString(), forHeader,
                        ctx.identifier().getText(), controlType.toString());
            } else if (!finalType.equiv(controlType)) {
                reportError(ctx, "Final Expression 【%s: %s】 " +
                                "of For Statement 【%s】\n " +
                                "must be the same ordinal type as control variable 【%s: %s】",
                        ctx.forList().finalValue().getText(),
                        finalType.toString(), forHeader,
                        ctx.identifier().getText(), controlType.toString());
            }
        }

        visit(ctx.statement());
        return null;
    }


    private String concatenateChildrenText(PascalParser.ForStatementContext ctx, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(ctx.getChild(i).getText()).append(" ");
        }
        return sb.toString();
    }

    @Override
    public TypeDescriptor visitAssignmentStatement(PascalParser.AssignmentStatementContext ctx) {
        TypeDescriptor rightType = visit(ctx.expression());
        String id = ctx.variable().getText();
        TypeDescriptor leftType = retrieve(id, ctx);

        if (rightType.equiv(Type.INVALID_CONSTANT_TYPE)) {
            Integer32 maxInt = getMaxInt(ctx);
            reportError(ctx, "Illegal assignment [%s] with invalid constant right operand [%s] " +
                            "which must be between [%d] and [%d]",
                    ctx.getText(), ctx.expression().getText(), maxInt.getMinValue(), maxInt.getMaxValue());
            return null;
        }

        if (!leftType.equiv(rightType)) {
            // exception case when left is real, it is acceptable though right is int
            if (leftType.equiv(Type.REAL) && rightType.equiv(Type.INTEGER)) return null;
            // exception case when left is character, and right is string literal(length must be 1)
            if (leftType.equiv(Type.CHARACTER) && rightType.equiv(Type.STRING_LITERAL)) {
                String content = ctx.expression().getText().replace("'", "");
                if (content.length() == 1) return null;
            }

            if (leftType instanceof Procedure) {
                reportError(ctx, "Assign value to a procedure:%s is not allowed", leftType);
                return null;
            }

            // check result type of a Function
            if (leftType instanceof Function) {
                Function function = (Function) leftType;
                TypeDescriptor resultType = function.getResultType();
                if (!rightType.equiv(resultType)) {

                    // exception case when left is real, it is acceptable though right is int
                    if (resultType.equiv(Type.REAL) && rightType.equiv(Type.INTEGER)) return function;
                    // exception case when left is character, and right is string literal(length must be 1)
                    if (resultType.equiv(Type.CHARACTER) && rightType.equiv(Type.STRING_LITERAL)) {
                        String content = ctx.expression().getText().replace("'", "");
                        if (content.length() == 1) return function;
                    }

                    reportError(ctx, "Assignment: [%s] failed, right operand type: %s cannot assigns to Function: %s",
                            ctx.getText(), rightType, function);
                }
                return function; // return Function itself for further check in the upper nodes
            }

            if (rightType instanceof Function) {
                Function function = (Function) rightType;
                TypeDescriptor resultType = function.getResultType();
                if (!leftType.equiv(resultType)) {

                    // exception case when left is real, it is acceptable though right is int
                    if (leftType.equiv(Type.REAL) && resultType.equiv(Type.INTEGER)) return function;
                    // exception case when left is character, and right is string literal(length must be 1)
                    if (leftType.equiv(Type.CHARACTER) && resultType.equiv(Type.STRING_LITERAL)) {
                        String content = ctx.expression().getText().replace("'", "");
                        if (content.length() == 1) return function;
                    }

                    reportError(ctx, "Assignment: [%s] failed, Function: %s result type cannot match the left operand: %s",
                            ctx.getText(), function, leftType);
                }
                return null;
            }

            System.out.println("ctx.getText(); = " + ctx.getText());
            reportError(ctx, "Assignment: [%s] types are incompatible! Expected(lType): %s Actual(rType): %s",
                    ctx.getText(), leftType.toString(), rightType.toString());
        }
        return null;
    }

    /**
     * ifStatement
     * : IF expression THEN statement (: ELSE statement)?
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitIfStatement(PascalParser.IfStatementContext ctx) {
        TypeDescriptor type = visit(ctx.expression());
        if (!type.equiv(Type.BOOLEAN)) {
            reportError(ctx, "Invalid condition type of if statement 【%s】", ctx.expression().getText());
        }
        ctx.statement().forEach(this::visit);
        return null;
    }

    /**
     * simple-type = ordinal-type | real-type
     * ordinal-type = enumerated-type | subrange-type | ordinal-type(int, boolean, char)
     *
     * @param type
     * @return
     */
    private boolean isSimpleType(TypeDescriptor type) {
        return (type.equiv(Type.REAL) || isOrdinalType(type));
    }

    /**
     * TODO: Enumerated types & Subrange NOT IMPLEMENTED
     * ordinal-type = new-ordinal-type(enumerated,subrange) | ordinal-type(int, boolean, char)
     *
     * @param type
     * @return
     */
    private boolean isOrdinalType(TypeDescriptor type) {
        return (type.equiv(Type.INTEGER) || type.equiv(Type.CHARACTER)
                || type.equiv(Type.BOOLEAN));
    }

    /**
     * expression
     * : simpleExpression (relationaloperator expression)?
     * Current Implementation:
     * - Only for simple types (Primitives & Str)
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitExpression(PascalParser.ExpressionContext ctx) {
        TypeDescriptor lType = visit(ctx.simpleExpression());
        TypeDescriptor rType = null;
        if (ctx.e2 != null) {
            rType = visit(ctx.e2);
            String operator = ctx.relationalOperator.getText();

            // check whether operands are simple types
            if (!(isSimpleType(lType))) {
                reportError(ctx, String.format(
                        "Relational operator 【%s】 cannot" +
                                " be applied on left operand 【%s】 of expression 【%s】: %s",
                        operator, ctx.simpleExpression().getText(), ctx.getText(), lType));
                return Type.INVALID_TYPE;
            }
            if (!(isSimpleType(rType))) {
                reportError(ctx, String.format("Relational operator 【%s】 cannot" +
                                " be applied on right operand 【%s】 of expression 【%s】: %s",
                        operator, ctx.expression().getText(), ctx.getText(), rType));
                return Type.INVALID_TYPE;
            }

            // relational expression
            if (!lType.equiv(rType)) {
                if (lType.equiv(Type.REAL) || rType.equiv(Type.REAL)) {
                    if (lType.equiv(Type.INTEGER) || rType.equiv(Type.INTEGER)) return Type.BOOLEAN;
                }
                if (lType.equiv(Type.CHARACTER) || rType.equiv(Type.CHARACTER)) {
                    if (lType.equiv(Type.STRING_LITERAL) || rType.equiv(Type.STRING_LITERAL)) return Type.BOOLEAN;
                }
                reportError(ctx, "Expression 【" + ctx.getText() + "】 types are incompatible! Type: " +
                        lType + " rType: " + rType);
                return Type.INVALID_TYPE;
            }

        }
        // if looping statement, return bool otherwise return type of simpleExpression()
        return rType != null ? Type.BOOLEAN : lType;
    }

    /**
     * SimpleExpression
     * 1. single var - return visit children
     * 2. var1 operator var2 - check on two sides
     * - operator could be
     * - additive operator: + - or(logical)
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitSimpleExpression(PascalParser.SimpleExpressionContext ctx) {
        TypeDescriptor lType = visit(ctx.term());
        TypeDescriptor rType = null;

        // if it involves 2 operands, need to check the type on both sides
        // then return the specific type
        if (null != ctx.simpleExpression()) {
            rType = visit(ctx.simpleExpression());
            String operator = ctx.additiveOperator.getText();

            // if is Logical operator OR
            if (operator.equalsIgnoreCase("or")) {
                return checkLogicalOpOperand(ctx, lType, ctx.term().getText(), rType, ctx.simpleExpression().getText(), operator);
            }

            // if left operand is not int nor real
            if (!lType.equiv(Type.INTEGER) && !lType.equiv(Type.REAL)) {
                reportError(ctx, "Additive Operator " + operator +
                        " cannot be applied on the left operand: " + lType);
                return Type.INVALID_TYPE;
            }
            // if right operand is not int nor real
            if (!rType.equiv(Type.INTEGER) && !rType.equiv(Type.REAL)) {
                reportError(ctx, "Additive Operator " + operator +
                        " cannot be applied on the right operand: " + rType);
                return Type.INVALID_TYPE;
            }
            if (lType.equiv(Type.REAL) || rType.equiv(Type.REAL)) return Type.REAL;
            else return Type.INTEGER;
        }
        // only 1 term
        return lType;
    }

    /**
     * Represents:
     * 1. single signedFactor - return visitChildren
     * 2. var1 multiplicative operator var2 - check both operands
     * - operator could be:
     * - *
     * - /
     * - div
     * - mod
     * - and (logical)
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitTerm(PascalParser.TermContext ctx) {
        TypeDescriptor lType = visit(ctx.signedFactor());
        TypeDescriptor rType = null;
        if (ctx.term() != null) {
            rType = visit(ctx.term());
        }

        // if it involves 2 operands, need to check the type on both sides
        // then return the specific type
        if (null != ctx.multiplicativeOperator) {
            String operator = ctx.multiplicativeOperator.getText();

            // if is Logical operator: AND
            if (operator.equalsIgnoreCase("and")) {
                return checkLogicalOpOperand(ctx, lType, ctx.signedFactor().getText(), rType, ctx.term().getText(), operator);
            }

            // other multiplicative operators(arithmetic)
            // if left operand is not int nor real
            if (!lType.equiv(Type.INTEGER) && !lType.equiv(Type.REAL)) {
                reportError(ctx, "Multiplicative Operator 【%s】 cannot be applied on the " +
                        "left operand 【%s】 of expression 【%s】: ", operator, ctx.signedFactor().getText(), ctx.getText());
                return Type.INVALID_TYPE;
            }
            // if right operand is not int nor real
            if (!rType.equiv(Type.INTEGER) && !rType.equiv(Type.REAL)) {
                reportError(ctx, "Multiplicative Operator 【%s】 cannot be applied on the " +
                        "right operand 【%s】 of expression 【%s】: ", operator, ctx.term().getText(), ctx.getText());
                return Type.INVALID_TYPE;
            }
            // integer division, operands must be integer
            if (operator.equals("div") || operator.equals("DIV")) {
                System.out.println("lType.equiv(Type.INTEGER) = " + lType.equiv(Type.INTEGER));
                System.out.println("rType.equiv(Type.INTEGER) = " + rType.equiv(Type.INTEGER));
                if (!lType.equiv(Type.INTEGER) || !rType.equiv(Type.INTEGER)) {
                    reportError(ctx, "The operands of integer division must be Integer: " +
                            "with lType: " + lType +
                            " with rType: " + rType);
                }
                return Type.INTEGER;
                // real division, operands could be int/real
            } else if (operator.equals("/")) {
                return Type.REAL;
            } else if (operator.equals("mod") || operator.equals("MOD")) {
                // modulus reminder division, operands must be integer
                if (!lType.equiv(Type.INTEGER) || !rType.equiv(Type.INTEGER)) {
                    reportError(ctx, "The operands of modulus must be Integer: " +
                            "with lType: " + lType +
                            " with rType: " + rType);
                }
                return Type.INTEGER;
            } else {
                // other multiplicative operators: *
                // return specific type
                if (lType.equiv(Type.REAL) || rType.equiv(Type.REAL)) return Type.REAL;
                else return Type.INTEGER;
            }
        }
        return lType;
    }

    private Type checkLogicalOpOperand(ParserRuleContext ctx, TypeDescriptor lType, String lOp, TypeDescriptor rType, String rOp, String operator) {
        if (!lType.equiv(Type.BOOLEAN)) {
            reportError(ctx, String.format("Logical operator 【%s】 cannot" +
                            " be applied on left operand 【%s】 of expression 【%s】: %s",
                    operator, lOp, ctx.getText(), lType));
            return Type.INVALID_TYPE;
        }
        if (!rType.equiv(Type.BOOLEAN)) {
            reportError(ctx, String.format("Logical operator 【%s】 cannot" +
                            " be applied on right operand 【%s】 of expression 【%s】: %s",
                    operator, rOp, ctx.getText(), rType));
            return Type.INVALID_TYPE;
        }
        return Type.BOOLEAN;
    }

    /**
     * signedFactor
     * : (PLUS | MINUS)? factor
     * ;
     * Check Monadic arithmetic operations:
     * -x
     * +x
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitSignedFactor(PascalParser.SignedFactorContext ctx) {
        TypeDescriptor type = visit(ctx.factor());
        // involves monadic arithmetic expression, check the operand type
        if (ctx.monadicOperator != null) {
            String monadicOp = ctx.monadicOperator.getText();
            if (!type.equiv(Type.INTEGER) && !type.equiv(Type.REAL)) {
                reportError(ctx, "Monadic arithmetic operator " + monadicOp +
                        " cannot be applied on the operand: " + type);
                return Type.INVALID_TYPE;
            }
        }
        return type;
    }

    /**
     * Children of Term-signedFactor
     * Represents a variable(identifier)
     *
     * @param ctx
     * @return
     */
    @Override
    public TypeDescriptor visitFactorVar(PascalParser.FactorVarContext ctx) {
        return retrieve(ctx.getText(), ctx);
    }

    @Override
    public TypeDescriptor visitFactorExpr(PascalParser.FactorExprContext ctx) {
        return visit(ctx.expression());
    }

    /**
     * NOT factor #notFactor
     * Logical operator
     *
     * @param ctx
     * @return boolean if valid
     */
    @Override
    public TypeDescriptor visitNotFactor(PascalParser.NotFactorContext ctx) {
        TypeDescriptor type = visit(ctx.factor());
        if (!type.equiv(Type.BOOLEAN)) {
            reportError(ctx, "Relational operator 【NOT】 cannot be applied on 【%s】 operand 【%s】: %s", ctx.getText(), ctx.factor().getText(), type.toString());
            return Type.INVALID_TYPE;
        }
        return type;
    }

    /**
     * NOTE: Int Overflows/Underflow shouldn't be checked in this node
     * <p>
     * Check whether unsigned int is smaller than maxInt
     * <p>
     * unsignedNumber
     * : unsignedInteger
     * | unsignedReal
     * ;
     *
     * @param ctx
     * @return
     */
    //@Override
    //@Deprecated
    //public Type visitUnsignedInteger(PascalParser.UnsignedIntegerContext ctx) {
    //    Integer32 maxInt = getMaxInt(ctx);
    //    Integer maxIntMaxValue = maxInt.getMaxValue();
    //    String rightOperand = ctx.getText();
    //    int rightOperandLength = ctx.getText().length();
    //
    //    // if overflows, set to default value of 0
    //    int rightOperandValue = NumberUtils.toInt(ctx.getText(), 0);
    //    // when value==0 && 0 value is derived from overflow
    //    if (rightOperandValue==0 && rightOperandLength!=1) {
    //        return Type.INVALID_CONSTANT_TYPE;
    //    }
    //    return Type.INTEGER;
    //}

    //@Override
    //public Type visitUnsignedReal(PascalParser.UnsignedRealContext ctx) {
    //    return Type.REAL;
    //}
    @Override
    public Type visitString(PascalParser.StringContext ctx) {
        return Type.STRING_LITERAL;
    }

    //@Override
    //public Type visitBool_(PascalParser.Bool_Context ctx) {
    //    return Type.BOOLEAN;
    //}


    @Override
    public Type visitTrue(PascalParser.TrueContext ctx) {
        return Type.BOOLEAN;
    }

    @Override
    public Type visitFalse(PascalParser.FalseContext ctx) {
        return Type.BOOLEAN;
    }
}
