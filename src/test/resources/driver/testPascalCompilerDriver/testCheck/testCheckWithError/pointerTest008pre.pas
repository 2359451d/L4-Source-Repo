(*
 * Project: pascal_test
 * User: Lenovo
 * Date: 30/01/2022
 *)
{$mode iso}
{$RANGECHECKS On}
program pointerTest008pre;
type
  p = ^recordType;
  a = Integer;
  recordType = record
    num: Integer;
    next: p;
  end;
var
  number: integer;
  iptr: ^integer;
  recordHeader, recordTail: p;
  recordVar: recordType;
  recordPtr: ^recordType;

  x, y : ^Integer;
begin
  x:= @recordHeader^.next;
  WriteLn(x^);
end.
