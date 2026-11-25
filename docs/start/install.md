---
icon: material/download-circle-outline
---

# Install

NexTerm is not on an app store yet: you install it from an **APK file** that you download from GitHub. It takes a minute, and you do it on the phone or tablet itself.

## 1. Download the APK

Open the [latest release](https://github.com/shad0w82/NexTerm/releases/latest) in the browser of the device and download the file called `NexTerm-<version>.apk` from the list of assets. The other items, “Source code”, are the code of the app and are not needed.

!!! tip "Check the download"
    The notes of the release give the **SHA-256** checksum of the APK. If you want to be sure the file arrived intact and is the one that was published, compute the checksum of the downloaded file (for example with `sha256sum` on a computer) and compare the two.

## 2. Open it

Open the downloaded file. The first time, Android says it does not let that app install other apps: it takes you to the setting called “Install unknown apps” (the name changes a little between manufacturers), where you allow the browser or the file manager you used. Go back and tap **Install**.

## 3. Start NexTerm

Open **NexTerm** from the launcher. It does not start by itself when you plug an adapter in; this is on purpose, so that plugging in a cable never opens an app you did not ask for.

Then follow [First connection](first-connection.md).

## Updating

Download the new APK and open it over the old one: profiles and saved commands stay.

!!! warning "The signature of the first builds"
    The 0.1.0 APK is signed with a **debug key**, not with a dedicated release key. A later build signed with a different key will not install over it: you would have to uninstall NexTerm first, and uninstalling removes the saved profiles and the settings, because NexTerm keeps no backup of them. Read the notes of a release before updating.

## Build it yourself

The source code is on [GitHub](https://github.com/shad0w82/NexTerm), with the [instructions to build it](https://github.com/shad0w82/NexTerm#building).
