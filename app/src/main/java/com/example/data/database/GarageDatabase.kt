package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.RoleEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import com.example.data.model.StockIssueEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TeamMemberEntity

@Database(
    entities = [
        JobCardEntity::class,
        CashItemEntity::class,
        InventoryItemEntity::class,
        ServiceMembershipEntity::class,
        CustomerProfileEntity::class,
        ServiceAlertEntity::class,
        TeamMemberEntity::class,
        StockIssueEntity::class,
        TeamEntity::class,
        RoleEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class GarageDatabase : RoomDatabase() {
    abstract val garageDao: GarageDao

    companion object {
        @Volatile
        private var INSTANCE: GarageDatabase? = null

        fun getDatabase(context: Context): GarageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GarageDatabase::class.java,
                    "garage_guru_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
