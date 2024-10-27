grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
COMMA : ',';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACK : '[' ;
RBRACK : ']' ;
VARARGS : '...' ;
MUL : '*' ;
ADD : '+' ;
DIV : '/' ;
SUB : '-' ;
NOT : '!' ;
AND : '&&' ;
LT : '<' ;
NEW : 'new' ;
DOT : '.' ;

CLASS : 'class' ;
IMPORT: 'import' ;
EXTENDS: 'extends' ;
INT : 'int' ;
BOOL : 'boolean' ;
STRING : 'String' ;
PUBLIC : 'public' ;
STATIC : 'static' ;
VOID: 'void' ;
RETURN : 'return' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
TRUE : 'true' ;
FALSE : 'false' ;
THIS : 'this' ;

INTEGER : '0' | [1-9][0-9]* ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

COMMENT : ('//'.*?[\n] | '/*'.*?'*/') -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID ( DOT name+=ID )* SEMI
    ;

classDecl
    : CLASS name=ID (EXTENDS parent=ID)?
        LCURLY
        varDecl*
        methodDecl*
        mainMethodDecl?
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : name=INT varArg=VARARGS
    | name=INT array=LBRACK RBRACK
    | name=INT
    | name=BOOL
    | name=STRING
    | name=ID
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN (param (COMMA param)*)? RPAREN
        LCURLY
        varDecl* stmt*
        returnStmt
        RCURLY
    ;

returnStmt
    : RETURN expr SEMI
    ;

mainMethodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
       STATIC VOID name=ID LPAREN STRING LBRACK RBRACK var=ID RPAREN
       LCURLY
       varDecl* stmt*
       RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : LCURLY stmt* RCURLY #BlockStmt //
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfStmt //
    | WHILE LPAREN expr RPAREN stmt #WhileStmt //
    | expr SEMI #ExprStmt //
    | name=ID EQUALS expr SEMI #AssignStmt //
    | name=ID LBRACK expr RBRACK EQUALS expr SEMI #ArrayAssignStmt //
    ;

expr
    : LPAREN expr RPAREN #ParenExpr //
    | expr DOT name=ID LPAREN (expr (COMMA expr)*)? RPAREN #MethodCallExpr //
    | expr DOT name=ID #ArrayLengthExpr //
    | expr LBRACK expr RBRACK #ArrayAccessExpr //
    | LBRACK (expr (COMMA expr)*)? RBRACK #ArrayExpr //
    | NEW INT LBRACK expr RBRACK #NewIntArrayExpr //
    | NEW name=ID LPAREN RPAREN #NewObjectExpr //
    | NOT expr #NotExpr //
    | expr op=(MUL | DIV) expr #BinaryExpr //
    | expr op=(ADD | SUB) expr #BinaryExpr //
    | expr op=LT expr #BinaryExpr //
    | expr op=AND expr #BinaryExpr //
    | value=(TRUE | FALSE) #BooleanLiteral //
    | value=INTEGER #IntegerLiteral //
    | THIS #ThisExpr //
    | name=ID #VarRefExpr //
    ;



