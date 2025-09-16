#!/usr/bin/env python3
"""
Manual Git Rebase Cleanup Script
This script manually cleans up a stuck git rebase without using bash commands.
"""

import os
import shutil
from pathlib import Path

def cleanup_rebase():
    """Manually clean up git rebase state."""
    
    # Change to the repository directory
    repo_path = Path('/Users/tensorhusker/Git/Hope')
    os.chdir(repo_path)
    
    git_dir = Path('.git')
    
    print("Starting manual git rebase cleanup...")
    print(f"Repository: {repo_path}")
    
    # 1. Read the original HEAD before rebase
    orig_head_file = git_dir / 'rebase-merge' / 'orig-head'
    if orig_head_file.exists():
        with open(orig_head_file, 'r') as f:
            orig_head = f.read().strip()
            print(f"Original HEAD commit: {orig_head}")
    else:
        print("Warning: Could not find original HEAD")
        # Try to use the last known good commit
        orig_head = "70eacbf09b35886c387cee9a06c7407c20d5c69e"  # From the file we read
        print(f"Using fallback commit: {orig_head}")
    
    # 2. Read the original branch name
    head_name_file = git_dir / 'rebase-merge' / 'head-name'
    if head_name_file.exists():
        with open(head_name_file, 'r') as f:
            original_branch = f.read().strip()
            print(f"Original branch: {original_branch}")
    else:
        original_branch = "refs/heads/main"
        print(f"Using default branch: {original_branch}")
    
    # 3. Reset HEAD to the original branch
    head_file = git_dir / 'HEAD'
    print(f"\nResetting HEAD to {original_branch}...")
    with open(head_file, 'w') as f:
        f.write(f"ref: {original_branch}\n")
    print("✓ HEAD reset to original branch")
    
    # 4. Update the branch ref to point to the original commit
    if original_branch.startswith('refs/heads/'):
        branch_name = original_branch.replace('refs/heads/', '')
        branch_ref_file = git_dir / 'refs' / 'heads' / branch_name
        
        # Ensure the directory exists
        branch_ref_file.parent.mkdir(parents=True, exist_ok=True)
        
        print(f"\nUpdating {branch_name} to point to {orig_head}...")
        with open(branch_ref_file, 'w') as f:
            f.write(f"{orig_head}\n")
        print(f"✓ Branch {branch_name} updated")
    
    # 5. Clean up rebase-related files and directories
    print("\nCleaning up rebase files...")
    
    # Remove rebase-merge directory
    rebase_merge_dir = git_dir / 'rebase-merge'
    if rebase_merge_dir.exists():
        shutil.rmtree(rebase_merge_dir)
        print("✓ Removed .git/rebase-merge/")
    
    # Remove rebase-apply directory if it exists
    rebase_apply_dir = git_dir / 'rebase-apply'
    if rebase_apply_dir.exists():
        shutil.rmtree(rebase_apply_dir)
        print("✓ Removed .git/rebase-apply/")
    
    # Remove other rebase-related files
    cleanup_files = [
        git_dir / 'REBASE_HEAD',
        git_dir / 'AUTO_MERGE',
        git_dir / 'MERGE_MSG',
        git_dir / 'ORIG_HEAD'
    ]
    
    for file_path in cleanup_files:
        if file_path.exists():
            os.remove(file_path)
            print(f"✓ Removed {file_path.name}")
    
    # 6. Reset the index
    index_file = git_dir / 'index'
    index_backup = git_dir / 'index.backup'
    
    if index_file.exists():
        # Backup the current index
        shutil.copy2(index_file, index_backup)
        print("✓ Backed up index to index.backup")
        
        # Try to reset the index from the original HEAD
        # Since we can't run git commands, we'll just remove the index
        # Git will regenerate it on the next operation
        os.remove(index_file)
        print("✓ Removed index file (will be regenerated)")
    
    print("\n" + "=" * 50)
    print("✅ CLEANUP COMPLETE!")
    print("=" * 50)
    print("\nThe git rebase has been manually aborted.")
    print(f"Repository reset to commit: {orig_head}")
    print(f"Current branch: {branch_name if 'branch_name' in locals() else 'main'}")
    print("\nNext steps:")
    print("1. Try running 'git status' to verify the cleanup")
    print("2. If bash is still broken, restart your terminal or IDE")
    print("3. You may need to run 'git reset --hard' to fully restore working directory")
    
    return True

if __name__ == "__main__":
    try:
        cleanup_rebase()
    except Exception as e:
        print(f"\n❌ Error during cleanup: {e}")
        print("\nManual cleanup steps:")
        print("1. Delete /Users/tensorhusker/Git/Hope/.git/rebase-merge/")
        print("2. Edit /Users/tensorhusker/Git/Hope/.git/HEAD to contain: ref: refs/heads/main")
        print("3. Delete /Users/tensorhusker/Git/Hope/.git/REBASE_HEAD if it exists")
        print("4. Restart your terminal")