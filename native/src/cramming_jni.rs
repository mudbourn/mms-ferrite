use std::slice;

use jni::objects::{JByteBuffer, JClass};
use jni::sys::jint;
use jni::JNIEnv;

use crate::cramming::{compute_cramming, CrammingInput, CrammingResult};

// Batched cramming push; failures return without writing so Java applies no deltas.
#[no_mangle]
pub extern "system" fn Java_info_mudbourn_ferrite_RustBridge_computeCramming<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    requests_buf: JByteBuffer<'local>,
    results_buf: JByteBuffer<'local>,
    entity_count: jint,
) {
    let count = entity_count.max(0) as usize;
    if count == 0 {
        return;
    }

    let Some(req_ptr) = env.get_direct_buffer_address(&requests_buf).ok() else {
        return;
    };
    let Some(req_cap) = env.get_direct_buffer_capacity(&requests_buf).ok() else {
        return;
    };
    let Some(res_ptr) = env.get_direct_buffer_address(&results_buf).ok() else {
        return;
    };
    let Some(res_cap) = env.get_direct_buffer_capacity(&results_buf).ok() else {
        return;
    };

    let request_bytes = count * std::mem::size_of::<CrammingInput>();
    let result_bytes = count * std::mem::size_of::<CrammingResult>();
    if req_cap < request_bytes || res_cap < result_bytes {
        let safe_count = res_cap / std::mem::size_of::<CrammingResult>();
        if safe_count > 0 {
            let results =
                unsafe { slice::from_raw_parts_mut(res_ptr as *mut CrammingResult, safe_count) };
            for r in results.iter_mut() {
                *r = CrammingResult::default();
            }
        }
        return;
    }

    let requests: &[CrammingInput] =
        unsafe { slice::from_raw_parts(req_ptr as *const CrammingInput, count) };
    let results: &mut [CrammingResult] =
        unsafe { slice::from_raw_parts_mut(res_ptr as *mut CrammingResult, count) };

    compute_cramming(requests, results);
}
