"""Every play payload declared under com/tacz/guns/network must be registered.

A ``CustomPacketPayload`` whose ``TYPE`` is never handed to ``PayloadTypeRegistry`` compiles
fine, but ``canSend(TYPE)`` silently stays false and the feature is dead on a real server.
That is exactly how AnimationParticleMessages (remote animation particles) was missing from
the 1.21.11 line while 26.1.2 / 26.3 registered it.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NETWORK = ROOT / "src/main/java/com/tacz/guns/network"
HANDLER = NETWORK / "NetworkHandler.java"

# `record X(...) implements CustomPacketPayload {` ... `Type<X> TYPE =`
RECORD = re.compile(r"\b(?:record|class)\s+(\w+)\b[^{]*?CustomPacketPayload")
TYPE_DECL = re.compile(r"Type<\s*(\w+)\s*>\s+TYPE\s*=")


def declared_payloads():
    found = set()
    for path in NETWORK.rglob("*.java"):
        if path == HANDLER:
            continue
        src = path.read_text(encoding="utf-8")
        outer = path.stem
        payload_types = set(RECORD.findall(src))
        for name in TYPE_DECL.findall(src):
            if name not in payload_types:
                continue
            found.add(outer if name == outer else f"{outer}.{name}")
    return found


class NetworkPayloadRegistrationTest(unittest.TestCase):
    def test_every_payload_type_is_registered_and_received(self):
        handler = HANDLER.read_text(encoding="utf-8")
        payloads = declared_payloads()
        self.assertIn("AnimationParticleMessages.Request", payloads)
        self.assertIn("AnimationParticleMessages.Delivery", payloads)
        missing_registry, missing_receiver = [], []
        for name in sorted(payloads):
            ref = re.escape(name) + r"\.TYPE"
            if not re.search(r"PayloadTypeRegistry\.play[CS]2[SC]\(\)\.register\(\s*" + ref, handler):
                missing_registry.append(name)
            if not re.search(r"registerGlobalReceiver\(\s*" + ref, handler):
                missing_receiver.append(name)
        self.assertEqual([], missing_registry, "payload TYPE never registered with PayloadTypeRegistry")
        self.assertEqual([], missing_receiver, "payload TYPE has no global receiver")


if __name__ == "__main__":
    unittest.main()
