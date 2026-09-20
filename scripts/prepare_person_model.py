"""Reproducibly freeze NanoDet initializer inputs without changing weights or operators.

Requires onnx==1.17.0, onnxruntime==1.20.0 and numpy. Runs locally; no image data or
network requests. Supply the pinned upstream ONNX as --source. --output must differ.
"""

import argparse
import hashlib
import json
from pathlib import Path
import statistics
import time

import numpy as np
import onnx
import onnxruntime as ort

SOURCE_SHA256 = "4b82da9944b88577175ee23a459dce2e26e6e4be573def65b1055dc2d9720186"


def session(model):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    options.log_severity_level = 3
    options.add_session_config_entry("session.intra_op.allow_spinning", "0")
    options.add_session_config_entry("session.inter_op.allow_spinning", "0")
    return ort.InferenceSession(model, options, providers=["CPUExecutionProvider"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.source.resolve() == args.output.resolve():
        raise ValueError("Keep the unmodified source separate from the derived model")
    source = args.source.read_bytes()
    if hashlib.sha256(source).hexdigest() != SOURCE_SHA256:
        raise ValueError("Source is not the pinned OpenCV Zoo model")
    model = onnx.load_model_from_string(source)
    original_nodes = [node.SerializeToString() for node in model.graph.node]
    original_weights = [value.SerializeToString() for value in model.graph.initializer]
    original_outputs = [value.SerializeToString() for value in model.graph.output]
    initializers = {value.name for value in model.graph.initializer}
    retained = [value for value in model.graph.input if value.name not in initializers]
    removed = len(model.graph.input) - len(retained)
    if removed == 0 or len(retained) != 1 or retained[0].name != "input.1":
        raise ValueError("Unexpected source graph contract")
    del model.graph.input[:]
    model.graph.input.extend(retained)
    onnx.checker.check_model(model)
    assert original_nodes == [node.SerializeToString() for node in model.graph.node]
    assert original_weights == [value.SerializeToString() for value in model.graph.initializer]
    assert original_outputs == [value.SerializeToString() for value in model.graph.output]
    optimized = model.SerializeToString()
    before, after = session(source), session(optimized)
    generator = np.random.default_rng(123)
    samples = [np.zeros((1, 3, 416, 416), dtype=np.float32)]
    samples.extend(generator.uniform(-2.2, 2.8, (1, 3, 416, 416)).astype(np.float32)
                   for _ in range(4))
    maximum_error = 0.0
    for image in samples:
        reference = before.run(None, {"input.1": image})
        candidate = after.run(None, {"input.1": image})
        for expected, actual in zip(reference, candidate, strict=True):
            assert expected.shape == actual.shape and np.isfinite(actual).all()
            maximum_error = max(maximum_error, float(np.max(np.abs(expected - actual))))
            np.testing.assert_allclose(actual, expected, atol=1e-5, rtol=1e-5)
    timings = [[], []]
    # Alternate order to reduce warmup/order bias; these are desktop checks, not mobile evidence.
    for iteration in range(36):
        for index in (range(2) if iteration % 2 == 0 else range(1, -1, -1)):
            started = time.perf_counter_ns()
            (before, after)[index].run(None, {"input.1": samples[iteration % len(samples)]})
            elapsed = (time.perf_counter_ns() - started) / 1e6
            if iteration >= 6:
                timings[index].append(elapsed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(optimized)
    print(json.dumps({
        "sourceSha256": SOURCE_SHA256,
        "outputSha256": hashlib.sha256(optimized).hexdigest(),
        "bytes": len(optimized), "removedInitializerInputs": removed,
        "parityInputs": len(samples), "maximumAbsoluteError": maximum_error,
        "unchangedNodes": len(original_nodes), "unchangedWeights": len(original_weights),
        "ortVersion": ort.__version__, "onnxVersion": onnx.__version__,
        "desktopCpuThreads": 1, "timedRunsEach": len(timings[0]),
        "originalMedianMs": statistics.median(timings[0]),
        "staticMedianMs": statistics.median(timings[1]),
        "notDevicePerformanceEvidence": True,
    }, indent=2))


if __name__ == "__main__":
    main()
