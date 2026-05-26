# AuraCam: Flagship Computational Camera (Premium DSLR Engine)

AuraCam is a completely original, professional-grade camera application optimized specifically for the **Samsung Galaxy A16 4G (SM-A165F/DS)** running **Samsung One UI**, powered by the **MediaTek Helio G99** chipset.

Instead of generic or heavy beauty smoothing, AuraCam utilizes a **Native C++ ARM NEON Core** to implement true physical optics and dynamic range adjustments, delivering mirrorless-grade rendering and cinematic portraiture.

---

## Key Core Highlights

1. **DSLR Progressive Disc Defocus:** Computes circular convolving disk blurs matching the apertures of 85mm f/1.4 lenses. Incorporates **Cat-Eye Vignetting** (horizontal compression near the corners of the sensor) and **Highlight Light-Bloom** (amplifying luminous background points to form glowing circles).
2. **Frequency-Separation Face Relighting:** Isolates skin zones to apply a $+4\%$ soft-box studio fill-light on low-frequency tones (shadow boundaries) while keeping high-frequency pore textures, eyelashes, and hair stubble $100\%$ untouched and sharp.
3. **Zero Shutter Lag (ZSL) Ring Buffer:** Continuously feeds raw `YUV_420_888` image analysis streams at 30 FPS. Clicking the shutter blends buffered frames, providing instant capture and preventing OS-level background freezes on One UI.
4. **Decoupled Clean Architecture:** decarates Presenter Compose layouts, business Domain contracts, and concrete hardware Data layers (CameraX and MediaStore scoped storage).

---

## How to Compile & Get Your APK via GitHub Actions (Cloud Build)

Since you do not want to install Android Studio or compiler tools locally, AuraCam is configured for **100% automated Cloud-Build CI/CD**. GitHub's cloud runners will compile the ARM64 C++ native code and package your APK.

### Step 1: Create a GitHub Repository
1. Log into your account on [GitHub](https://github.com).
2. Create a new **Public** or **Private** repository named `AuraCam` (do **not** check "Add a README" or ".gitignore" options during setup).

### Step 2: Push Your Local Code to GitHub
Open **PowerShell** or **Git Bash** in your local workspace folder (`C:\Users\Mahmoud\Desktop\a16`), and execute the following commands to push the project:

```bash
# Initialize local git repository
git init

# Add all project source files and CI/CD workflow configurations
git add .

# Create the initial commit
git commit -m "feat: init AuraCam flagship clean architecture and NDK DSLR engine"

# Rename default branch to main
git branch -M main

# Link to your new GitHub repository (replace with your actual GitHub username)
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/AuraCam.git

# Push code to GitHub (triggers the automated cloud compiler immediately)
git push -u origin main
```

### Step 3: Download Your Compiled APK
1. Open your repository on the GitHub website.
2. Click on the **Actions** tab at the top.
3. You will see a running workflow named **"AuraCam CI/CD Cloud Build"** matching your push commit.
4. Once the build completes successfully (approx. 2-3 minutes):
   * Click on the completed workflow run.
   * Scroll down to the **Artifacts** section at the bottom.
   * Click on **`AuraCam-Debug-APK`** to download your compiled, fully optimized Android installer!
