package ru.kotlin.senin

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.kotlin.senin.GitHubRepositoryStatistics.LoadingStatus.*
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.table.DefaultTableModel
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

val log: Logger = LoggerFactory.getLogger("AppUI")
private val defaultInsets = Insets(3, 10, 3, 10)


data class UserStatistics(
    val commits: Int,
    val files: Set<String>,
    val changes: Int
)

fun main() {
    setDefaultFontSize(18f)
    GitHubRepositoryStatistics().apply {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }
}

class GitHubRepositoryStatistics : JFrame("GitHub Repository Statistics"), CoroutineScope {

    enum class LoadingStatus { COMPLETED, CANCELED, IN_PROGRESS }

    companion object {
        private val columns = arrayOf("Author", "Commits", "Files", "Changes")
        const val textFieldWidth = 30
    }

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private fun init() {
        // Start a new loading on 'load' click
        addLoadListener {
            saveParameters()
            loadResults()
        }

        // Save preferences and exit on closing the window
        addOnWindowClosingListener {
            job.cancel()
            saveParameters()
            exitProcess(0)
        }

        // Load stored params (user & password values)
        loadInitialParameters()
    }

    private fun loadResults() {
        val (username, password, repositoryUrl) = getParameters()
        val (owner, repository) = parseRepositoryUrl(repositoryUrl)
        val req = RequestData(username, password, owner, repository)

        clearResults()
        val service = createGitHubService(req.username, req.password)

        val startTime = System.currentTimeMillis()
        launch(Dispatchers.Default) {
            log.info("Loading results...")
            loadResults(service, req) { users, completed ->
                withContext(Dispatchers.Main) {
                    updateResults(users, startTime, completed)
                }
            }
        }.setUpCancellation()
    }

    private fun parseRepositoryUrl(repositoryUrl: String): Pair<String, String>  {
        val splitted = repositoryUrl.split('/')
        if (splitted[2] != "github.com" ||
                splitted[0] != "https:" || splitted[1] != "") {
            throw Exception("Wrong URL")
        }
        return Pair(repositoryUrl.split('/')[3],
            repositoryUrl.split('/')[4])
    }

    private fun clearResults() {
        updateResults(emptyMap())
        updateLoadingStatus(IN_PROGRESS)
        setActionsStatus(newLoadingEnabled = false)
    }

    private fun updateResults(results: Map<String, UserStatistics>, startTime: Long, completed: Boolean = true) {
        updateResults(results)
        updateLoadingStatus(if (completed) COMPLETED else IN_PROGRESS, startTime)
        if (completed) {
            setActionsStatus(newLoadingEnabled = true)
        }
    }

    private fun updateLoadingStatus(status: LoadingStatus, startTime: Long? = null) {
        val time = if (startTime != null) {
            val time = System.currentTimeMillis() - startTime
            "${(time / 1000)}.${time % 1000 / 100} sec"
        } else ""

        val text = "Loading status: " +
                when (status) {
                    COMPLETED -> "completed in $time"
                    IN_PROGRESS -> "in progress $time"
                    CANCELED -> "canceled"
                }
        setLoadingStatus(text, status == IN_PROGRESS)
    }

    private fun Job.setUpCancellation() {
        setActionsStatus(newLoadingEnabled = false, cancellationEnabled = true)

        val loadingJob = this

        // cancel the loading job if the 'cancel' button was clicked
        val listener = ActionListener {
            loadingJob.cancel()
            updateLoadingStatus(CANCELED)
        }
        addCancelListener(listener)

        // update the status and remove the listener after the loading job is completed
        launch {
            loadingJob.join()
            setActionsStatus(newLoadingEnabled = true)
            removeCancelListener(listener)
        }
        // TODO: make active the 'cancel' button
        // TODO: cancel the loading job if the 'cancel' button was clicked
        // TODO: update the status and remove the listener after the loading job is completed
    }

    private fun loadInitialParameters() {
        setParameters(loadParameters())
    }

    private fun saveParameters() {
        val parameters = getParameters()
        if (parameters.username.isEmpty() && parameters.password.isEmpty()) {
            removeStoredParameters()
        } else {
            saveParameters(parameters)
        }
    }

    private val username = JTextField(textFieldWidth)
    private val password = JPasswordField(textFieldWidth)
    private val repositoryUrl = JTextField(textFieldWidth)
    private val load = JButton("Load statistics")
    private val cancel = JButton("Cancel").apply { isEnabled = false }

    private val resultsModel = DefaultTableModel(columns, 0)
    private val results = JTable(resultsModel)
    private val resultsScroll = JScrollPane(results).apply {
        preferredSize = Dimension(200, 600)
    }

    private val loadingIcon = ImageIcon(javaClass.classLoader.getResource("ajax-loader.gif"))
    private val loadingStatus = JLabel("Start new loading", loadingIcon, SwingConstants.CENTER)

    init {
        // Create UI
        rootPane.contentPane = JPanel(GridBagLayout()).apply {
            addLabeled("GitHub Username", username)
            addLabeled("Password/Token", password)
            addWideSeparator()
            addLabeled("Repository url", repositoryUrl)
            addWideSeparator()
            addWide(JPanel().apply {
                add(load)
                add(cancel)
            })
            addWide(resultsScroll) {
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
            addWide(loadingStatus)
        }

        // Initialize actions
        init()
    }

    private fun updateResults(results: Map<String, UserStatistics>) {
        // TODO: Sort results by number of commits!
        resultsModel.setDataVector(results.map {it ->it.key to it.value}.sortedByDescending { it.second.commits }.map { (login, stat) ->
            arrayOf(login, stat.commits, stat.files.size, stat.changes)
        }.toTypedArray(), columns)
    }

    private fun setLoadingStatus(text: String, iconRunning: Boolean) {
        loadingStatus.text = text
        loadingStatus.icon = if (iconRunning) loadingIcon else null
    }

    private fun addCancelListener(listener: ActionListener) {
        cancel.addActionListener(listener)
    }

    private fun removeCancelListener(listener: ActionListener) {
        cancel.removeActionListener(listener)
    }

    private fun addLoadListener(listener: () -> Unit) {
        load.addActionListener { listener() }
    }

    private fun addOnWindowClosingListener(listener: () -> Unit) {
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                listener()
            }
        })
    }

    private fun setActionsStatus(newLoadingEnabled: Boolean, cancellationEnabled: Boolean = false) {
        load.isEnabled = newLoadingEnabled
        cancel.isEnabled = cancellationEnabled
    }

    private fun setParameters(storedParameters: StoredParameters) {
        username.text = storedParameters.username
        password.text = storedParameters.password
        repositoryUrl.text = storedParameters.repositoryUrl
    }

    private fun getParameters(): StoredParameters {
        return StoredParameters(username.text, password.password.joinToString(""), repositoryUrl.text)
    }
}

fun JPanel.addLabeled(label: String, component: JComponent) {
    add(JLabel(label), GridBagConstraints().apply {
        gridx = 0
        insets = defaultInsets
    })
    add(component, GridBagConstraints().apply {
        gridx = 1
        insets = defaultInsets
        anchor = GridBagConstraints.WEST
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
    })
}

fun JPanel.addWide(component: JComponent, constraints: GridBagConstraints.() -> Unit = {}) {
    add(component, GridBagConstraints().apply {
        gridx = 0
        gridwidth = 2
        insets = defaultInsets
        constraints()
    })
}

fun JPanel.addWideSeparator() {
    addWide(JSeparator()) {
        fill = GridBagConstraints.HORIZONTAL
    }
}

fun setDefaultFontSize(size: Float) {
    for (key in UIManager.getLookAndFeelDefaults().keys.toTypedArray()) {
        if (key.toString().toLowerCase().contains("font")) {
            val font = UIManager.getDefaults().getFont(key) ?: continue
            val newFont = font.deriveFont(size)
            UIManager.put(key, newFont)
        }
    }
}

private fun preferencesNode(): Preferences = Preferences.userRoot().node("AppUI")

data class StoredParameters(val username: String, val password: String, val repositoryUrl: String)

fun loadParameters(): StoredParameters {
    return preferencesNode().run {
        StoredParameters(
            get("username", ""),
            get("password", ""),
            get("repositoryUrl", "https://github.com/Kotlin/kotlinx.coroutines")
        )
    }
}

fun removeStoredParameters() {
    preferencesNode().removeNode()
}

fun saveParameters(storedParameters: StoredParameters) {
    preferencesNode().apply {
        put("username", storedParameters.username)
        put("password", storedParameters.password)
        put("repositoryUrl", storedParameters.repositoryUrl)
        sync()
    }
}

fun correctDate(date : String) : Boolean {
    val lst = date.split('T')[0].split('-').joinToString("")
    return lst >= "20210430"
}

suspend fun loadResults(
    service: GitHubService, req: RequestData,
    updateResults: suspend (Map<String, UserStatistics>, completed: Boolean) -> Unit): Unit = coroutineScope {
    var page = 1
    var repos : List<Commit>
    val result: MutableMap<String, UserStatistics> = mutableMapOf()
    repos = service.getCommits(owner = req.owner, repository = req.repository, page = page).body()?.filter {
        it.author?.type != "Bot" && it.author != null && correctDate(it.commit.author.date)
    } ?: listOf()
    page++
    val channel = Channel<List<CommitWithChanges>?> ()
    launch {
        var it = 0
        val listChanges : MutableList<CommitWithChanges> = mutableListOf()
        while (repos?.size != 0) {
            repos.forEach { repo ->
                val changes = service
                    .getChanges(owner = req.owner, repository = req.repository, commitRef = repo.sha)
                    .body()
                if (changes != null) {
                    listChanges.add(changes)
                    if (listChanges.size % 20 == 0) {
                        channel.send(listChanges)
                        listChanges.clear()
                    }
                }
            }
            page++
            repos = service.getCommits(owner = req.owner, repository = req.repository, page = page).body()?.filter {
                it.author?.type != "Bot" && it.author != null
            } ?: listOf()
        }
        channel.send(null)
    }
    launch {
        var commit = channel.receive()
        var it = 0
        while (commit != null) {
            commit.let{ listChanges ->
                listChanges.forEach {
                    when (it != null && it.author != null) {
                        true -> {
                            val stat = UserStatistics(1, it.files.map { it.filename }.toSet(),
                                it.files.sumOf { it.changes })
                            val temp = result.getOrDefault(it.author.login, UserStatistics(0, setOf(), 0))
                            result[it.author.login] = UserStatistics(
                                stat.commits + temp.commits, stat.files + temp.files,
                                stat.changes + temp.changes
                            )
                        }
                    }
                }
            }
            commit = channel.receive()
            updateResults(result, commit == null)
            it++
        }
    }
}

