
# Mobile ARM Disassembler

Mobile ARM Disassembler is a powerful Android application for reverse engineering and analyzing ELF (Executable and Linkable Format) files on ARM architecture. Built with **Kotlin**, **Jetpack Compose**, and a **C++ native library**, it offers a modern, intuitive interface for loading ELF files, parsing sections and symbols, and visualizing disassembled instructions, hex dumps, symbols, bookmarks, and control flow graphs. The app leverages the Android Storage Access Framework (SAF) for secure file access and a high-performance native library for efficient ELF parsing and ARM disassembly.

## Features

- **ELF File Loading**: Load ELF files (e.g., `.elf`, `.o`, executables) via SAF or legacy storage permissions.
- **Disassembly View**: Display ARM instructions with addresses, mnemonics, operands, and comments, supporting search and bookmarking.
- **Hex Viewer**: Inspect raw hex data and ASCII representations of ELF sections.
- **Symbol Browser**: View and filter ELF symbols by name, address, or section.
- **Bookmark Management**: Add, edit, and remove bookmarks with custom names and comments.
- **Control Flow Graphs**: Visualize code flow with interactive, zoomable graphs.
- **Search Functionality**: Filter instructions and symbols by mnemonic, operand, address, or name.
- **Responsive UI**: Built with Jetpack Compose, supporting light and dark themes with Material 3 design.
- **Native Performance**: Optimized C++ library (`mobilearmdisassembler`) for ELF parsing and disassembly, using C++17 and link-time optimization (LTO).

## Prerequisites

- **Android Studio**: Latest stable version (e.g., Koala 2024.1.1 or later).
- **JDK**: Version 17 (required for Android Gradle Plugin 8.11.1).
- **Gradle**: Version 8.14.3 or higher.
- **Android SDK**: `minSdk` 24, `targetSdk` 35.
- **CMake**: Version 3.22.1 for building the native library.
- **NDK**: Android Native Development Kit (e.g., version 27.0.12077973) for C++ compilation.
- **ktlint**: For Kotlin code formatting (optional, for CI validation).

## Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/IMTIAZ-X/KTIMAZ-REV.git
   cd KTIMAZ-REV
   ```

2. **Open in Android Studio**:
   - Launch Android Studio and open the cloned project.

3. **Configure JDK 17**:
   - Go to **File > Project Structure > SDK Location > JDK** and select a JDK 17 installation (e.g., Eclipse Temurin 17).
   - Alternatively, set the `JAVA_HOME` environment variable to a JDK 17 path (e.g., `/usr/lib/jvm/temurin-17-jdk-amd64`).
   - Optionally, add `org.gradle.java.home=/path/to/jdk17` to `gradle.properties`.

4. **Install Android NDK**:
   - In Android Studio, go to **Tools > SDK Manager > SDK Tools**, select **NDK (Side by Side)**, and install version 27.0.12077973 or compatible.

5. **Configure CMake**:
   - Ensure CMake 3.22.1 is installed via **Tools > SDK Manager > SDK Tools**.

6. **Sync Project**:
   - Run the following to download dependencies and sync the project:
     ```bash
     ./gradlew sync
     ```

7. **(Optional) Set Up ktlint**:
   - For code formatting, run:
     ```bash
     ./gradlew ktlintCheck
     ```
   - To apply formatting fixes:
     ```bash
     ./gradlew ktlintFormat
     ```

## Usage

1. **Run the Application**:
   - Select an emulator or physical device (API level 24 or higher) in Android Studio.
   - Run the `app` configuration to launch the app.

2. **Load an ELF File**:
   - Tap the folder icon in the top app bar to open the file picker.
   - Select an ELF file (e.g., `.elf`, `.o`, or executable formats).
   - The app parses the file and displays available sections and symbols.

3. **Navigate Tabs**:
   - **Disassembly**: View and search ARM instructions, add bookmarks, and view comments.
   - **Hex View**: Inspect section hex data and ASCII representations.
   - **Symbols**: Browse and filter ELF symbols by name or address.
   - **Bookmarks**: Manage bookmarks with custom names and comments.
   - **Graph View**: Visualize control flow graphs with zoom and pan support.

4. **Search and Filter**:
   - Use the search bar in the Disassembly and Symbols tabs to filter by mnemonic, operand, address, or symbol name.

5. **Build APKs**:
   - **Debug APK**:
     ```bash
     ./gradlew assembleDebug
     ```
   - **Release APK** (requires signing configuration in `gradle.properties`):
     ```bash
     ./gradlew assembleRelease
     ```

## Project Structure

```
KTIMAZ-REV/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                    # C++ native code (native_lib.cpp, elf_parser.cpp, arm_disassembler.cpp, utils.cpp)
│   │   │   ├── java/com/imtiaz/ktimazstudio/
│   │   │   │   ├── model/              # Data models (Symbol.kt, Bookmark.kt, Instruction.kt)
│   │   │   │   ├── ui/                 # Jetpack Compose UI components (GraphCanvas.kt, HexViewer.kt, DisassemblyView.kt, etc.)
│   │   │   │   ├── ui/theme/           # Theme and typography (Color.kt, Theme.kt, Type.kt)
│   │   │   │   ├── utils/              # Utilities (FilePicker.kt, ThreadPool.kt)
│   │   │   │   └── viewmodel/          # ViewModels (DisassemblyViewModel.kt, FileLoaderViewModel.kt)
│   │   │   ├── res/
│   │   │   │   └── values/             # Resources (strings.xml, themes.xml)
│   │   │   └── AndroidManifest.xml     # Application manifest
│   ├── build.gradle.kts                # App module build configuration
│   └── proguard-rules.pro              # ProGuard rules for release builds
├── gradle/                             # Gradle wrapper files
├── .gitignore                          # Git ignore file
├── build.gradle.kts                    # Top-level build configuration
├── CMakeLists.txt                      # CMake configuration for native library
└── README.md                           # Project documentation
```

## Dependencies

- **Kotlin**: 2.0.21 with Coroutines for asynchronous operations.
- **Jetpack Compose**: 1.7.4 for UI, Material 3 for design.
- **AndroidX**: Core, AppCompat, Activity, Lifecycle, and ViewModel for app functionality.
- **Native Library**: `mobilearmdisassembler` (C++17, linked with Android `log` library).
- **Testing**: JUnit 4.13.2, Espresso 3.6.1, and Compose UI testing 1.7.4.

## Building the Native Library

The app uses a C++ native library (`mobilearmdisassembler`) for ELF parsing and ARM disassembly. To build:

1. Ensure the NDK and CMake are installed (see Prerequisites).
2. The `CMakeLists.txt` configures the library with source files (`native_lib.cpp`, `elf_parser.cpp`, `arm_disassembler.cpp`, `utils.cpp`).
3. Build the native library with:
   ```bash
   ./gradlew build
   ```
4. The library is automatically included in the APK during the build process.

## Contributing

We welcome contributions to enhance Mobile ARM Disassembler! To contribute:

1. **Fork the Repository**:
   ```bash
   git clone https://github.com/IMTIAZ-X/KTIMAZ-REV.git
   cd KTIMAZ-REV
   ```

2. **Create a Feature Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make Changes**:
   - Follow Kotlin coding standards and run `ktlintCheck` to ensure formatting.
   - Add or update unit tests in `app/src/test` or instrumentation tests in `app/src/androidTest`.
   - Update documentation if new features are added.

4. **Commit Changes**:
   ```bash
   git commit -m "Add your feature description"
   ```

5. **Push to Your Fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Open a Pull Request**:
   - Submit a pull request to the `main` branch with a clear description of your changes.
   - Ensure the CI pipeline (`ci.yml`) passes all checks (linting, tests, and builds).

## Continuous Integration

The project uses GitHub Actions for CI/CD, defined in `.github/workflows/ci.yml`. The pipeline:
- Runs on pushes and pull requests to `main` and `develop` branches.
- Validates Kotlin code with `ktlintCheck`.
- Runs linting (`./gradlew lint`) and unit tests (`./gradlew test`).
- Builds debug and release APKs (`./gradlew assembleDebug`, `assembleRelease`).
- Uploads APKs as artifacts.
- Deploys release APKs to Firebase App Distribution (configurable for other platforms).

To set up CI:
- Configure signing keys in GitHub Secrets (`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`, `KEYSTORE_PATH`).
- For Firebase deployment, add `FIREBASE_APP_ID` and `FIREBASE_SERVICE_ACCOUNT` secrets.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Notes

- **Storage Access**: Uses SAF for file access on Android 11+ (API 30+). For API 29 and below, requests `READ_EXTERNAL_STORAGE` permission.
- **Native Library**: Optimized with C++17 and LTO. Capstone integration is optional (commented in `CMakeLists.txt`).
- **Known Issues**:
  - Ensure all imports use `com.imtiaz.ktimazstudio` (not `com.example.disassembler`).
  - String resources are defined in `res/values/strings.xml`. Verify they match your app’s requirements.
- **Future Enhancements**:
  - Support for additional architectures (e.g., x86, AArch64).
  - Integration with Capstone for advanced disassembly.
  - Enhanced graph visualization with cross-reference support.
  - Export functionality for bookmarks and annotations.

## Contact

For questions, bug reports, or feature requests, please open an issue on the [GitHub repository](https://github.com/IMTIAZ-X/KTIMAZ-REV) or contact the maintainers.

## Acknowledgments

- Built with love for reverse engineering and Android development.
- Thanks to the open-source community for tools like Jetpack Compose, Kotlin, and Android NDK.

---
*Last updated: August 2025*
