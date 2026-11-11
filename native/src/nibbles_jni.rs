use jni::objects::{JByteArray, JClass};
use jni::sys::{jboolean, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::nibbles::{shift_nibbles, LAYER_BYTES};

// Shifts one light layer into dst; returns false without writing on a bad array.
#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_shiftNibbles<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    src: JByteArray<'local>,
    dst: JByteArray<'local>,
    delta: jint,
) -> jboolean {
    let mut input = [0i8; LAYER_BYTES];
    if env.get_array_length(&src).ok() != Some(LAYER_BYTES as i32)
        || env.get_array_length(&dst).ok() != Some(LAYER_BYTES as i32)
        || env.get_byte_array_region(&src, 0, &mut input).is_err()
    {
        return JNI_FALSE;
    }

    let mut output = [0u8; LAYER_BYTES];
    shift_nibbles(as_unsigned(&input), &mut output, delta);
    let output: [i8; LAYER_BYTES] = output.map(|b| b as i8);
    if env.set_byte_array_region(&dst, 0, &output).is_err() {
        return JNI_FALSE;
    }
    JNI_TRUE
}

fn as_unsigned(bytes: &[i8; LAYER_BYTES]) -> &[u8; LAYER_BYTES] {
    unsafe { &*(bytes as *const [i8; LAYER_BYTES] as *const [u8; LAYER_BYTES]) }
}
