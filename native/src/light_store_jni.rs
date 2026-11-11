use jni::objects::{JByteArray, JClass};
use jni::sys::{jboolean, jlong, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::light_store::STORE;
use crate::nibbles::LAYER_BYTES;

fn flag(value: bool) -> jboolean {
    if value {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

fn read_layer(env: &JNIEnv, array: &JByteArray) -> Option<[u8; LAYER_BYTES]> {
    if env.get_array_length(array).ok()? != LAYER_BYTES as i32 {
        return None;
    }
    let mut signed = [0i8; LAYER_BYTES];
    env.get_byte_array_region(array, 0, &mut signed).ok()?;
    Some(signed.map(|b| b as u8))
}

// Compresses one layer into the store, replacing any layer at the same key.
#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_lightStorePut<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    owner: jlong,
    pos: jlong,
    layer: JByteArray<'local>,
) -> jboolean {
    let Some(bytes) = read_layer(&env, &layer) else {
        return JNI_FALSE;
    };
    let Ok(mut store) = STORE.lock() else {
        return JNI_FALSE;
    };
    flag(store.put(owner, pos, &bytes))
}

// Decompresses one layer into out, leaving it in the store.
#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_lightStoreGet<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    owner: jlong,
    pos: jlong,
    out: JByteArray<'local>,
) -> jboolean {
    if env.get_array_length(&out).ok() != Some(LAYER_BYTES as i32) {
        return JNI_FALSE;
    }
    let mut bytes = [0u8; LAYER_BYTES];
    let found = match STORE.lock() {
        Ok(store) => store.get(owner, pos, &mut bytes),
        Err(_) => false,
    };
    if !found {
        return JNI_FALSE;
    }
    let signed = bytes.map(|b| b as i8);
    flag(env.set_byte_array_region(&out, 0, &signed).is_ok())
}

#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_lightStoreRemove<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    owner: jlong,
    pos: jlong,
) -> jboolean {
    flag(STORE.lock().map(|mut s| s.remove(owner, pos)).unwrap_or(false))
}

#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_lightStoreClearOwner<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    owner: jlong,
) {
    if let Ok(mut store) = STORE.lock() {
        store.clear_owner(owner);
    }
}

#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_lightStoreCount<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    STORE.lock().map(|s| s.len() as jlong).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_lightStoreBytes<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    STORE.lock().map(|s| s.compressed_bytes() as jlong).unwrap_or(0)
}
