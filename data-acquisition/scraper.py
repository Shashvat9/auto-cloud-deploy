# scraper.py
import os
import subprocess
from github import Github, GithubException
from config import GITHUB_TOKEN, TEMP_DIR, GITHUB_SEARCH_QUERY


def get_terraform_repos(limit=50):
    """
    Finds and clones popular Terraform repositories from GitHub.
    Returns a dictionary of {repo_name: local_path}.
    """
    try:
        g = Github(GITHUB_TOKEN)
        query = GITHUB_SEARCH_QUERY
        print(f"Searching GitHub with query: '{query}'")
        repositories = g.search_repositories(query=query)

        cloned_repos = {}
        count = 0

        for repo in repositories:
            if count >= limit:
                break

            repo_path = os.path.join(TEMP_DIR, repo.full_name.replace('/', '_'))

            if os.path.exists(repo_path):
                print(f"Repository '{repo.full_name}' already exists locally. Skipping clone.")
                continue

            print(f"Cloning '{repo.full_name}' into '{repo_path}'...")
            try:
                # Using subprocess to clone for better performance with large repos
                subprocess.run(
                    ["git", "clone", "--depth", "1", repo.clone_url, repo_path],
                    check=True,
                    capture_output=True,
                    text=True
                )
                cloned_repos[repo.full_name] = repo_path
                count += 1
            except subprocess.CalledProcessError as e:
                print(f"Failed to clone {repo.full_name}. Error: {e.stderr}")
            except Exception as e:
                print(f"An unexpected error occurred during clone: {e}")

        return cloned_repos

    except GithubException as e:
        print(f"GitHub API Error: {e.status} - {e.data}")
        print("Please check your GITHUB_TOKEN in config.py. Using an invalid or rate-limited token can cause this.")
        return {}
    except Exception as e:
        print(f"An unexpected error occurred while scraping GitHub: {e}")
        return {}
