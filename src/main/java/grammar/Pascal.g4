/*
BSD License

Copyright (c) 2013, Tom Everett
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
3. Neither the name of Tom Everett nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
Adapted from pascal.g by  Hakki Dogusan, Piet Schoutteten and Marton Papp
*/

grammar Pascal;

//@header{
//	package src/main/java/grammar;
//}

program
   : programHeading (INTERFACE)? block DOT
   ;

programHeading
   : PROGRAM identifier (LPAREN identifierList RPAREN)? SEMI
   | UNIT identifier SEMI
   ;

identifier
   : IDENT
   ;

block
   : (labelDeclarationPart | constantDefinitionPart | typeDefinitionPart | variableDeclarationPart | procedureAndFunctionDeclarationPart | usesUnitsPart | IMPLEMENTATION)* compoundStatement
   ;

usesUnitsPart
   : USES identifierList SEMI
   ;

labelDeclarationPart
   : LABEL label (COMMA label)* SEMI
   ;

label
    :
    NUM_INT
//   : unsignedInteger
   ;

constantDefinitionPart
   : CONST (constantDefinition SEMI) +
   ;

constantDefinition
   : identifier EQUAL constant
   ;

constantChr
//   : CHR LPAREN unsignedInteger RPAREN
    : CHR LPAREN NUM_INT RPAREN
   ;

// id defined by constant-definition
// one of values of an enumeratedType types
// one of the standard id: false, true, maxint
constant
   : unsignedNumber #unsignedNumberConst
   | sign unsignedNumber #signedNumberConst
   | identifier #constantIdentifier
   | sign identifier #constantSignedIdentifier
   | string #stringConst
   | constantChr #chrConst // chr(int) , ordinal func
   | bool_ #boolConst
//   | TRUE #trueConst
//   | FALSE #falseConst
   ;

unsignedNumber
    :
    type=(NUM_INT | NUM_REAL)
   ;

//unsignedInteger
//   : NUM_INT
//   ;
//
//unsignedReal
//   : NUM_REAL
//   ;

sign
   : PLUS
   | MINUS
   ;

bool_
   : TRUE # true
   | FALSE # false
   ;

string
   : STRING_LITERAL
   ;

typeDefinitionPart
   : TYPE (typeDefinition SEMI) +
   ;

// ! func, proc type definition is not standard
typeDefinition
//   : identifier EQUAL (type_ | functionType | procedureType)
   : identifier EQUAL type_
   ;

functionType
   : FUNCTION (formalParameterList)? COLON resultType
   ;

procedureType
   : PROCEDURE (formalParameterList)?
   ;

type_
   : simpleType
   | structuredType
   | pointerType
   ;

simpleType
   : scalarType
   | subrangeType
   | typeIdentifier
   | stringtype
   ;

scalarType
   : LPAREN identifierList RPAREN   # enumeratedType
   ;

subrangeType
   : constant DOTDOT constant
   ;

typeIdentifier
   : identifier                                 # typeId
   | primitiveType=(CHAR | BOOLEAN | INTEGER | REAL | STRING)  # primitiveType
   ;

structuredType
   : PACKED unpackedStructuredType
   | unpackedStructuredType
   ;

unpackedStructuredType
   : arrayType
   | recordType
   | setType
   | fileType
   ;

stringtype
   : STRING LBRACK (identifier | unsignedNumber) RBRACK
   ;

arrayType
   : ARRAY LBRACK typeList RBRACK OF componentType
   | ARRAY LBRACK2 typeList RBRACK2 OF componentType
   ;

typeList
   : indexType (COMMA indexType)*
   ;

indexType
   : simpleType
   ;

componentType
   : type_
   ;

recordType
   : RECORD fieldList? END
   ;

fieldList
   : fixedPart (SEMI variantPart)? (SEMI)?
   | variantPart (SEMI)?
   ;

fixedPart
   : recordSection (SEMI recordSection)*
   ;

recordSection
   : identifierList COLON type_
   ;

variantPart
   : CASE tag OF variant (SEMI variant)*
   ;

tag
   : identifier COLON typeIdentifier
   | typeIdentifier
   ;

variant
   : constList COLON LPAREN fieldList RPAREN
   ;

setType
   : SET OF baseType
   ;

baseType
   : simpleType
   ;

fileType
   : FILE OF type_
   | FILE
   ;

pointerType
   : POINTER typeIdentifier
   ;

variableDeclarationPart
   : VAR variableDeclaration (SEMI variableDeclaration)* SEMI
   ;

variableDeclaration
   : identifierList COLON type_
   ;

procedureAndFunctionDeclarationPart
   : procedureOrFunctionDeclaration SEMI
   ;

procedureOrFunctionDeclaration
   : procedureDeclaration
   | functionDeclaration
   ;

directive
   : FORWARD
   ;

procedureDeclaration
   : procedureHeading SEMI directive #procedurePrototypeDecl
   | PROCEDURE identifier SEMI block #procedureImpl
   | PROCEDURE identifier (formalParameterList)? SEMI block #procedureDecl
   ;

formalParameterList
   : LPAREN formalParameterSection (SEMI formalParameterSection)* RPAREN
   ;

formalParameterSection
   : parameterGroup  #noLabelParam
   | VAR parameterGroup #varLabelParam
   | functionHeading #funcParam
   | procedureHeading #procParam
//   | FUNCTION parameterGroup #funcParam
//   | PROCEDURE parameterGroup #procParam
   ;

functionHeading
   : FUNCTION identifier (formalParameterList)? COLON resultType
   ;

procedureHeading
   : PROCEDURE identifier (formalParameterList)?
   ;


parameterGroup
   : identifierList COLON typeIdentifier
   ;

identifierList
   : identifier (COMMA identifier)*
   ;

constList
   : constant (COMMA constant)*
   ;


functionDeclaration
   : functionHeading SEMI directive #functionPrototypeDecl
   | FUNCTION identifier SEMI block #functionImpl
   | FUNCTION identifier (formalParameterList)? COLON resultType SEMI block #functionDecl
   ;

resultType
   : typeIdentifier
   ;

statement
   : label COLON unlabelledStatement
   | unlabelledStatement
   ;

unlabelledStatement
   : simpleStatement
   | structuredStatement
   ;

simpleStatement
   : assignmentStatement
   | procedureStatement
   | gotoStatement
   | emptyStatement
   ;

assignmentStatement
   : variable ASSIGN expression
   ;

variable
   : variableHead
   (
   arrayScripting
   | fieldDesignator
   | POINTER // only for pointer variable
   )*
//   (LBRACK expression (COMMA expression)* RBRACK // array scripting
//   | LBRACK2 expression (COMMA expression)* RBRACK2 // array scripting
   ;

//referencedVariable
//    : variable POINTER+
//    ;

variableHead
   : (AT identifier | identifier)
   ;

arrayScripting
  : LBRACK expression (COMMA expression)* RBRACK // array scripting
   | LBRACK2 expression (COMMA expression)* RBRACK2 // array scripting
   ;

fieldDesignator
  : DOT identifier
  ;


expression
   : simpleExpression (relationalOperator=(EQUAL| NOT_EQUAL| LT| LE| GE| GT| IN)
                        e2=expression)?
   ;

//relationaloperator
//   : EQUAL
//   | NOT_EQUAL
//   | LT
//   | LE
//   | GE
//   | GT
//   | IN
//   ;

simpleExpression
   : term (additiveOperator=(PLUS| MINUS| OR) simpleExpression)?
   ;

//additiveoperator
//   : PLUS
//   | MINUS
//   | OR
//   ;

term
   : signedFactor (multiplicativeOperator=(STAR| SLASH| DIV| MOD| AND) term)?
   ;

//multiplicativeoperator
//   : STAR
//   | SLASH
//   | DIV
//   | MOD
//   | AND
//   ;

signedFactor
   : monadicOperator=(PLUS | MINUS)? factor
   ;

factor
   : variable   # factorVar
   | LPAREN expression RPAREN #factorExpr
   | functionDesignator #factorFuncDesignator // function call
   | unsignedConstant #factorUnConst
   | set_ #factorSet
   | NOT factor #notFactor
   | bool_ #factorBool
   ;

unsignedConstant
   : unsignedNumber
   | constantChr
   | string
   | NIL
   ;

functionDesignator
   : identifier LPAREN parameterList RPAREN
   ;

parameterList
   : actualParameter (COMMA actualParameter)*
   | writeParameters
   | readParameters
   ;



set_
   : LBRACK elementList RBRACK
   | LBRACK2 elementList RBRACK2
   ;

elementList
   : element (COMMA element)*
   |
   ;

element
   : expression (DOTDOT expression)?
   ;

procedureStatement
   : readProcedureStatement
   | writeProcedureStatement
   | identifier (LPAREN parameterList RPAREN)?
   ;

writeProcedureStatement
   :(WRITE|WRITELN) (LPAREN writeParameters RPAREN)?
   ;

readProcedureStatement
   : (READ|READLN) (LPAREN readParameters RPAREN)?
   ;

// output value must be of type int,reasl,bool,char, or a string
// bool - True, False
writeParameters
   : (outputValue (COMMA outputValue)*)*
   ;

outputValue
   : expression (':' expression (':' expression)*)*
   ;


readParameters
   : inputValue (COMMA inputValue)*
   ;

inputValue
   : variable
   ;

actualParameter
   : expression parameterwidth*
   ;

parameterwidth
   : ':' expression
   ;

gotoStatement
   : GOTO label
   ;

emptyStatement
   :
   ;

empty_
   :
   /* empty */
   ;

structuredStatement
   : compoundStatement
   | conditionalStatement
   | repetetiveStatement
   | withStatement
   ;

compoundStatement
   : BEGIN statements END
   ;

statements
   : statement (SEMI statement)*
   ;

conditionalStatement
   : ifStatement
   | caseStatement
   ;

ifStatement
   : IF expression THEN statement (: ELSE statement)?
   ;

caseStatement
   : CASE expression OF caseListElement (SEMI caseListElement)* (SEMI ELSE statements)? (SEMI)? END
   ;

caseListElement
   : constList COLON statement
   ;

repetetiveStatement
   : whileStatement
   | repeatStatement
   | forStatement
   ;

whileStatement
   : WHILE expression DO statement
   ;

repeatStatement
   : REPEAT statements UNTIL expression
   ;

forStatement
   : FOR identifier ASSIGN forList DO statement
   ;

forList
   : initialValue (TO | DOWNTO) finalValue
   ;

initialValue
   : expression
   ;

finalValue
   : expression
   ;

withStatement
   : WITH recordVariableList DO statement
   ;

recordVariableList
   : variable (COMMA variable)*
   ;


fragment A
   : ('a' | 'A')
   ;


fragment B
   : ('b' | 'B')
   ;


fragment C
   : ('c' | 'C')
   ;


fragment D
   : ('d' | 'D')
   ;


fragment E
   : ('e' | 'E')
   ;


fragment F
   : ('f' | 'F')
   ;


fragment G
   : ('g' | 'G')
   ;


fragment H
   : ('h' | 'H')
   ;


fragment I
   : ('i' | 'I')
   ;


fragment J
   : ('j' | 'J')
   ;


fragment K
   : ('k' | 'K')
   ;


fragment L
   : ('l' | 'L')
   ;


fragment M
   : ('m' | 'M')
   ;


fragment N
   : ('n' | 'N')
   ;


fragment O
   : ('o' | 'O')
   ;


fragment P
   : ('p' | 'P')
   ;


fragment Q
   : ('q' | 'Q')
   ;


fragment R
   : ('r' | 'R')
   ;


fragment S
   : ('s' | 'S')
   ;


fragment T
   : ('t' | 'T')
   ;


fragment U
   : ('u' | 'U')
   ;


fragment V
   : ('v' | 'V')
   ;


fragment W
   : ('w' | 'W')
   ;


fragment X
   : ('x' | 'X')
   ;


fragment Y
   : ('y' | 'Y')
   ;


fragment Z
   : ('z' | 'Z')
   ;

WRITE
   : W R I T E
   ;

WRITELN
   : W R I T E L N
   ;

READ
   : R E A D
   ;

READLN
   : R E A D L N
   ;

AND
   : A N D
   ;


ARRAY
   : A R R A Y
   ;


BEGIN
   : B E G I N
   ;


BOOLEAN
   : B O O L E A N
   ;


CASE
   : C A S E
   ;


CHAR
   : C H A R
   ;


CHR
   : C H R
   ;


CONST
   : C O N S T
   ;


DIV
   : D I V
   ;


DO
   : D O
   ;


DOWNTO
   : D O W N T O
   ;


ELSE
   : E L S E
   ;


END
   : E N D
   ;


FILE
   : F I L E
   ;


FOR
   : F O R
   ;


FUNCTION
   : F U N C T I O N
   ;


GOTO
   : G O T O
   ;


IF
   : I F
   ;


IN
   : I N
   ;


INTEGER
   : I N T E G E R
   ;


LABEL
   : L A B E L
   ;


MOD
   : M O D
   ;


NIL
   : N I L
   ;


NOT
   : N O T
   ;


OF
   : O F
   ;


OR
   : O R
   ;


PACKED
   : P A C K E D
   ;


PROCEDURE
   : P R O C E D U R E
   ;


PROGRAM
   : P R O G R A M
   ;


REAL
   : R E A L
   ;


RECORD
   : R E C O R D
   ;


REPEAT
   : R E P E A T
   ;


SET
   : S E T
   ;


THEN
   : T H E N
   ;


TO
   : T O
   ;


TYPE
   : T Y P E
   ;


UNTIL
   : U N T I L
   ;


VAR
   : V A R
   ;


WHILE
   : W H I L E
   ;


WITH
   : W I T H
   ;


PLUS
   : '+'
   ;


MINUS
   : '-'
   ;


STAR
   : '*'
   ;


SLASH
   : '/'
   ;


ASSIGN
   : ':='
   ;


COMMA
   : ','
   ;


SEMI
   : ';'
   ;


COLON
   : ':'
   ;


EQUAL
   : '='
   ;


NOT_EQUAL
   : '<>'
   ;


LT
   : '<'
   ;


LE
   : '<='
   ;


GE
   : '>='
   ;


GT
   : '>'
   ;


LPAREN
   : '('
   ;


RPAREN
   : ')'
   ;


LBRACK
   : '['
   ;


LBRACK2
   : '(.'
   ;


RBRACK
   : ']'
   ;


RBRACK2
   : '.)'
   ;


POINTER
   : '^'
//   | '↑' // standard representation
   ;


AT
   : '@'
   ;


DOT
   : '.'
   ;


DOTDOT
   : '..'
   ;


LCURLY
   : '{'
   ;


RCURLY
   : '}'
   ;


UNIT
   : U N I T
   ;


INTERFACE
   : I N T E R F A C E
   ;


USES
   : U S E S
   ;


STRING
   : S T R I N G
   ;


IMPLEMENTATION
   : I M P L E M E N T A T I O N
   ;


TRUE
   : T R U E
   ;


FALSE
   : F A L S E
   ;

// directives
FORWARD
   : F O R W A R D
   ;


WS
   : [ \t\r\n] -> skip
   ;


COMMENT_1
   : '(*' .*? '*)' -> skip
   ;


COMMENT_2
   : '{' .*? '}' -> skip
   ;


IDENT
   : ('a' .. 'z' | 'A' .. 'Z') ('a' .. 'z' | 'A' .. 'Z' | '0' .. '9' | '_')*
   ;


STRING_LITERAL
   : '\'' ('\'\'' | ~ ('\''))* '\''
   ;


NUM_INT
   : ('0' .. '9') +
   ;


NUM_REAL // unsigned int seq E scale-factor | unsigned real_num seq E scale-factor
   : ('0' .. '9') + (('.' ('0' .. '9') + (EXPONENT)?)? | EXPONENT)
   ;


fragment EXPONENT
   : ('e') ('+' | '-')? ('0' .. '9') +
   ;
