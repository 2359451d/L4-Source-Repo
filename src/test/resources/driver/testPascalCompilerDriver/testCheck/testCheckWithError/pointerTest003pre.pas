(*
 * Project: pascal_test
 * User: Lenovo
 * Date: 30/01/2022
 *)
{$mode iso}
{$RANGECHECKS On}
program pointerTest003pre;
type
  p = ^recordType;
  recordType = record
    num: Integer;
    next: p;
  end;
var
  recordHeader, recordTail: p;
begin
  recordHeader^.num :=999;
end.
