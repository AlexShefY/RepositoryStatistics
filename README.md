# GitHub Repository Statistics 

As part of the task you need to implement a GUI application to 
to get data about commits of a given GitHub repository.

## Data retrieval 

The program must receive all data via the GitHub API.  
Authorization is required to work with private repositories, 
for which you need to get a token on the 
[https://github.com/settings/tokens/new](https://github.com/settings/tokens/new).
When generating the token, you only need to specify scope `repo`. The token is only needed for testing,
after testing it can be deleted at [https://github.com/settings/tokens/](https://github.com/settings/tokens/).  

**Do not save the token in the repository!**

Queries with authorization must have the header `Authorization: Basic <authorization token>`.
This `<authorization token>` is derived from the user's login and its token 
as the base64-encoded string `<username>:<password>`.


#### Getting a list of commits

См. https://docs.github.com/en/rest/commits/commits

#### Getting commit data  

См. https://docs.github.com/en/rest/commits/commits#get-a-commit

###  Requirements for the application

The user of the application specifies:
1. GitHub login for authorization
2. token for authorization
Repository URL (for example `https://github.com/Kotlin/kotlinx.coroutines`) 

Using this data after pressing the `Load statistics` button, the application must build a table of the form: 
```
Author          Commits                    Files                               Changes
<Summit author> <Total number of commits> <Total number of affected files> <Total number of changes
```

Implementation requirements:

1. You need to exclude commits whose author had type `Bot`. 
2. You need to exclude commits made more than 12 months ago 
3. The results in the table should be sorted in descending order by number of commits  
4. If the same file was changed in different commits, you have to count it once. All changes from different commits are just summed up.
5. When loading data, UI must not be blocked.
6. The results table must be dynamically updated as the data arrive
7. Need to support the ability to cancel loading (button `Cancel`)
8. Need to use kotlinx.coroutines   

## Tests

We need to implement tests to check the correctness of results using `retrofit-mock`.
