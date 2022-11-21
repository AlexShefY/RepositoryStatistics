# GitHub Repository Statistics 

## Data retrieval 

The program receives all data via the GitHub API.  
Authorization is required to work with private repositories, 
for which you need to get a token on the 
[https://github.com/settings/tokens/new](https://github.com/settings/tokens/new).
When generating the token, you only need to specify scope `repo`. The token is only needed for testing,
after testing it can be deleted at [https://github.com/settings/tokens/](https://github.com/settings/tokens/).  

Queries with authorization have the header `Authorization: Basic <authorization token>`.
This `<authorization token>` is derived from the user's login and its token 
as the base64-encoded string `<username>:<password>`.

###  Application

The user of the application specifies:
1. GitHub login for authorization
2. token for authorization
Repository URL (for example `https://github.com/Kotlin/kotlinx.coroutines`) 

Using this data after pressing the `Load statistics` button, the application builds a table of the form: 
```
Author          Commits                    Files                               Changes
<Summit author> <Total number of commits> <Total number of affected files> <Total number of changes
```

Implementation:

1. I exclude commits whose author had type `Bot`. 
2. I exclude commits made more than 12 months ago 
3. The results in the table are sorted in descending order by number of commits  
4. If the same file was changed in different commits, I count it once. All changes from different commits are just summed up.
5. When loading data, UI are not blocked.
6. The results table are dynamically updated as the data arrive
7. It's supported the ability to cancel loading (button `Cancel`)
8. I use kotlinx.coroutines   

## Tests

I implemented tests to check the correctness of results using `retrofit-mock`.
