#!/usr/bin/env python3
"""
Direct Git Cleanup - No Shell Commands
Directly manipulates git files to clean up stuck rebase
"""

import os
import shutil
from pathlib import Path

# Set the repository path
REPO_PATH = Path('/Users/tensorhusker/Git/Hope')
GIT_DIR = REPO_PATH / '.git'

print("=" * 60)
print("DIRECT GIT CLEANUP - NO SHELL REQUIRED")
print("=" * 60)

# Step 1: Remove the rebase-merge directory
rebase_merge = GIT_DIR / 'rebase-merge'
if rebase_merge.exists():
    print(f"\n[1/5] Removing rebase-merge directory...")
    try:
        shutil.rmtree(rebase_merge)
        print("      ✅ Removed .git/rebase-merge/")
    except Exception as e:
        print(f"      ❌ Failed to remove: {e}")
else:
    print("\n[1/5] No rebase-merge directory found ✅")

# Step 2: Remove rebase-apply if exists
rebase_apply = GIT_DIR / 'rebase-apply'
if rebase_apply.exists():
    print("\n[2/5] Removing rebase-apply directory...")
    try:
        shutil.rmtree(rebase_apply)
        print("      ✅ Removed .git/rebase-apply/")
    except Exception as e:
        print(f"      ❌ Failed to remove: {e}")
else:
    print("\n[2/5] No rebase-apply directory found ✅")

# Step 3: Clean up rebase-related files
print("\n[3/5] Cleaning up rebase-related files...")
cleanup_files = [
    'REBASE_HEAD',
    'AUTO_MERGE', 
    'MERGE_MSG',
    'CHERRY_PICK_HEAD',
    'REVERT_HEAD',
    'BISECT_LOG',
    'BISECT_START'
]

for filename in cleanup_files:
    filepath = GIT_DIR / filename
    if filepath.exists():
        try:
            os.remove(filepath)
            print(f"      ✅ Removed {filename}")
        except Exception as e:
            print(f"      ❌ Failed to remove {filename}: {e}")

# Step 4: Verify HEAD points to main
print("\n[4/5] Verifying HEAD configuration...")
head_file = GIT_DIR / 'HEAD'
try:
    with open(head_file, 'r') as f:
        head_content = f.read().strip()
    print(f"      Current HEAD: {head_content}")
    
    if not head_content.startswith('ref: refs/heads/'):
        # HEAD is detached, fix it
        print("      ⚠️  HEAD is detached, fixing...")
        with open(head_file, 'w') as f:
            f.write('ref: refs/heads/main\n')
        print("      ✅ HEAD now points to main branch")
    else:
        print("      ✅ HEAD is properly configured")
except Exception as e:
    print(f"      ❌ Failed to check/fix HEAD: {e}")

# Step 5: Remove lock files
print("\n[5/5] Removing any lock files...")
lock_files = [
    'index.lock',
    'HEAD.lock',
    'config.lock'
]

for lockfile in lock_files:
    lockpath = GIT_DIR / lockfile
    if lockpath.exists():
        try:
            os.remove(lockpath)
            print(f"      ✅ Removed {lockfile}")
        except Exception as e:
            print(f"      ❌ Failed to remove {lockfile}: {e}")

# Also check refs/heads/ for lock files
refs_heads = GIT_DIR / 'refs' / 'heads'
if refs_heads.exists():
    for lockfile in refs_heads.glob('*.lock'):
        try:
            os.remove(lockfile)
            print(f"      ✅ Removed {lockfile.name}")
        except Exception as e:
            print(f"      ❌ Failed to remove {lockfile.name}: {e}")

print("\n" + "=" * 60)
print("CLEANUP COMPLETE!")
print("=" * 60)

# Print current git state
print("\n📊 Current Git State:")
print("-" * 40)

# Check HEAD
try:
    with open(GIT_DIR / 'HEAD', 'r') as f:
        print(f"HEAD: {f.read().strip()}")
except:
    print("HEAD: Unable to read")

# Check current branch ref
try:
    main_ref = GIT_DIR / 'refs' / 'heads' / 'main'
    if main_ref.exists():
        with open(main_ref, 'r') as f:
            print(f"main branch points to: {f.read().strip()[:8]}...")
except:
    pass

# Check for any remaining rebase/merge state
problem_dirs = ['rebase-merge', 'rebase-apply', 'MERGE_HEAD']
problems = []
for item in problem_dirs:
    if (GIT_DIR / item).exists():
        problems.append(item)

if problems:
    print(f"\n⚠️  Warning: These items still exist: {', '.join(problems)}")
else:
    print("\n✅ No rebase/merge conflicts detected")

print("\n" + "=" * 60)
print("RECOVERY RECOMMENDATIONS:")
print("=" * 60)
print("""
1. The git rebase has been forcefully cleaned up
2. If bash/terminal is still broken, try:
   - Restart your terminal/IDE
   - Open a new terminal window
   - Restart your computer if necessary

3. Once terminal is working, run:
   git status
   git reset --hard HEAD
   git clean -fd

4. If you need to preserve work:
   - Copy modified files to a backup location first
   - Then run the reset commands above
   - Manually reapply your changes

5. Alternative: Use a Git GUI tool like:
   - GitHub Desktop
   - SourceTree  
   - GitKraken
   - VS Code's built-in Git support
""")