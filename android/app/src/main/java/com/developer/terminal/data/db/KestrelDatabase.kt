package com.developer.terminal.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_packages")
data class InstalledPackageEntity(
    @PrimaryKey val name: String,
    val version: String,
    val description: String,
    val architecture: String,
    val repository: String,
    val sizeBytes: Long,
    val dependenciesCsv: String,
    val installPath: String,
    val isNativeExecutable: Boolean,
    val installedAt: Long
)

@Entity(tableName = "package_repositories")
data class PackageRepoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val indexUrl: String,
    val arch: String,
    val enabled: Boolean
)

@Entity(tableName = "package_logs")
data class PackageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val action: String,
    val status: String,
    val message: String,
    val timestamp: Long
)

@Entity(tableName = "command_aliases")
data class AliasEntity(
    @PrimaryKey val name: String,
    val command: String
)

@Entity(tableName = "env_vars")
data class EnvVarEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "terminal_profiles")
data class TerminalProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shellPath: String,
    val startupCommand: String,
    val themeId: String,
    val fontSizeSp: Int
)

@Entity(tableName = "automation_tasks")
data class AutomationTaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val command: String,
    val intervalMinutes: Int,
    val enabled: Boolean,
    val lastRunAt: Long,
    val lastOutput: String
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rootPath: String,
    val createdAt: Long
)

@Dao
interface KestrelDao {
    @Query("SELECT * FROM installed_packages ORDER BY name ASC")
    fun observeInstalledPackages(): Flow<List<InstalledPackageEntity>>

    @Query("SELECT * FROM installed_packages ORDER BY name ASC")
    suspend fun getInstalledPackages(): List<InstalledPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackage(pkg: InstalledPackageEntity)

    @Query("DELETE FROM installed_packages WHERE name = :name")
    suspend fun deletePackage(name: String)

    @Query("SELECT * FROM package_repositories ORDER BY name ASC")
    fun observeRepositories(): Flow<List<PackageRepoEntity>>

    @Query("SELECT * FROM package_repositories WHERE enabled = 1")
    suspend fun getEnabledRepositories(): List<PackageRepoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepository(repo: PackageRepoEntity)

    @Query("DELETE FROM package_repositories WHERE id = :id")
    suspend fun deleteRepository(id: String)

    @Query("SELECT * FROM package_logs ORDER BY timestamp DESC LIMIT 100")
    fun observePackageLogs(): Flow<List<PackageLogEntity>>

    @Insert
    suspend fun insertPackageLog(log: PackageLogEntity)

    @Query("SELECT * FROM command_aliases ORDER BY name ASC")
    fun observeAliases(): Flow<List<AliasEntity>>

    @Query("SELECT * FROM command_aliases ORDER BY name ASC")
    suspend fun getAliases(): List<AliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: AliasEntity)

    @Query("DELETE FROM command_aliases WHERE name = :name")
    suspend fun deleteAlias(name: String)

    @Query("SELECT * FROM env_vars ORDER BY `key` ASC")
    fun observeEnvVars(): Flow<List<EnvVarEntity>>

    @Query("SELECT * FROM env_vars ORDER BY `key` ASC")
    suspend fun getEnvVars(): List<EnvVarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnvVar(envVar: EnvVarEntity)

    @Query("DELETE FROM env_vars WHERE `key` = :key")
    suspend fun deleteEnvVar(key: String)

    @Query("SELECT * FROM terminal_profiles ORDER BY name ASC")
    fun observeProfiles(): Flow<List<TerminalProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: TerminalProfileEntity)

    @Query("DELETE FROM terminal_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM automation_tasks ORDER BY name ASC")
    fun observeTasks(): Flow<List<AutomationTaskEntity>>

    @Query("SELECT * FROM automation_tasks ORDER BY name ASC")
    suspend fun getTasks(): List<AutomationTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: AutomationTaskEntity)

    @Query("DELETE FROM automation_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("SELECT * FROM workspaces ORDER BY createdAt DESC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(ws: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspace(id: String)
}

@Database(
    entities = [
        InstalledPackageEntity::class,
        PackageRepoEntity::class,
        PackageLogEntity::class,
        AliasEntity::class,
        EnvVarEntity::class,
        TerminalProfileEntity::class,
        AutomationTaskEntity::class,
        WorkspaceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KestrelDatabase : RoomDatabase() {
    abstract fun dao(): KestrelDao

    companion object {
        @Volatile
        private var INSTANCE: KestrelDatabase? = null

        fun getInstance(context: Context): KestrelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KestrelDatabase::class.java,
                    "kestrel_workspace.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
