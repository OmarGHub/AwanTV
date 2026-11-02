Awan TV Installer: licences of what it is made of

The installer's code is under the MIT licence in AwanTV.txt (Awan TV's changes; Afnon's installer, which it started
from, github.com/egyjs/afnon-android-tv-launcher). Its full source is in the Awan TV repository (installer/), so it
can be rebuilt with modified versions of the libraries below.

Libraries in this app:
1. libadb-android 3.1.1 by Muntashir Al-Islam (github.com/MuntashirAkon/libadb-android): GPL-3.0-or-later or
   Apache-2.0; used here under Apache-2.0 (Apache-2.0.txt).
2. spake2-java 2.2.1 by Muntashir Al-Islam (github.com/MuntashirAkon/spake2-java), brought by libadb-android:
   LGPL-3.0 (LGPL-3.0.txt, which adds to the GPL-3.0 in GPL-3.0.txt). Its Ed25519 code is from EdDSA-Java (CC0).
3. Bouncy Castle (bcprov) by the Legion of the Bouncy Castle, brought by libadb-android: MIT (BouncyCastle.txt).
4. AndroidX Annotation by The Android Open Source Project, brought by libadb-android: Apache-2.0 (Apache-2.0.txt).
5. Conscrypt 2.5.3 by The Android Open Source Project (github.com/google/conscrypt): Apache-2.0 (Apache-2.0.txt,
   its notice in Conscrypt-NOTICE.txt, and its Netty and Apache Harmony parts in LICENSE.netty.txt and
   LICENSE.harmony.txt).
6. BoringSSL by Google, built into Conscrypt (boringssl.googlesource.com): the OpenSSL, SSLeay and ISC licences
   (BoringSSL.txt).
7. The Kotlin standard library and JetBrains annotations, brought by the libraries above: Apache-2.0 (Apache-2.0.txt).
8. The fonts Alexandria and Amiri: SIL Open Font License 1.1 (OFL.txt).
