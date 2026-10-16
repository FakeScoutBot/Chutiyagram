# scout_voip overlay

Patched copy of `tgcalls/group/GroupAudioCapturePostProcessor.{h,cpp}` (voice changer hook)
plus `VoicePitchShifter.{h,cpp}`. Upstream moved tgcalls into the submodule
`TMessagesProj/jni/tgcalls`, so we keep our changes here instead of editing the submodule.

- Based on TelegramMessenger/tgcalls @ ccbb57965020768c94f5b205ea08547a7e0fe49d
- `scout_voip` MUST stay first in `target_include_directories(tgcalls ...)` so that
  `#include "group/GroupAudioCapturePostProcessor.h"` inside the submodule resolves to this copy
  (otherwise the class layout differs between translation units -> memory corruption).
- When upstream bumps the tgcalls submodule: diff the new
  `tgcalls/group/GroupAudioCapturePostProcessor.*` against the base commit above and re-apply the
  "Voice changer" block here, then update the commit hash.
