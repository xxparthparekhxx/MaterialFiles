# PR to open once GitHub GraphQL quota resets

Blocked on: secondary/primary GraphQL rate limit (5000/hr) while paginating the project board.
Everything else is done: branch pushed, build + lint green, commit `74a64761`.

- Repo (fork only, NEVER upstream): xxparthparekhxx/MaterialFiles
- Base: master
- Head: feature/localized-error-messages
- Title: Show localized, user friendly error messages
- Body: /tmp/opencode/pr-body.md
- Compare link: https://github.com/xxparthparekhxx/MaterialFiles/compare/master...feature/localized-error-messages?expand=1

Create it with:

    cd /tmp/opencode/mf-localized-errors && \
      gh pr create --repo xxparthparekhxx/MaterialFiles \
        --base master --head feature/localized-error-messages \
        --title "Show localized, user friendly error messages" \
        --body-file /tmp/opencode/pr-body.md

Or just retry later; the branch is already on origin, so only the PR itself is missing.