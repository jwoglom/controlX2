# WearX2

Android Wear OS and Phone companion app for controlling a Tandem pump with [PumpX2](https://github.com/jwoglom/pumpx2)
from a wearable.

## Setup

Assumes that you have PumpX2 built and published to the local Maven repository. You can do this with:

```bash
$ git clone https://github.com/jwoglom/pumpx2
$ cd pumpx2
$ ./gradlew build
$ ./gradlew publishToMavenLocal 
```

The PumpX2 library files will be published to `$HOME/.m2/repository/com/jwoglom/pumpx2/`.

If the PumpX2 libraries are rebuilt, either bump the version number in both PumpX2 and WearX2's gradle
configurations or run `./gradlew build --refresh-dependencies`.