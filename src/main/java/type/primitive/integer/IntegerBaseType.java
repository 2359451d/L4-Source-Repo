package type.primitive.integer;

import type.TypeDescriptor;
import type.primitive.Primitive;

public class IntegerBaseType extends Primitive {

    // default size - Integer32 (4B)
    public static final Integer MAX_VALUE = Integer.MAX_VALUE;
    public static final Integer MIN_VALUE = Integer.MIN_VALUE;

    // use Long type in case overflow/underflow
    private Long value;

    public IntegerBaseType() {
        super("int");
    }

    public IntegerBaseType(String type) {
        super(type);
    }

    public static IntegerBaseType copy(IntegerBaseType from) {
        IntegerBaseType copy = new IntegerBaseType();
        copy.setValue(from.getValue());
        return copy;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    @Override
    public boolean equiv(TypeDescriptor type) {
        return type instanceof IntegerBaseType;
    }
}