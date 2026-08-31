package com.josiel.organizeprocesso.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PessoaEntity::class,
        FaseEntity::class,
        ProcessoEntity::class,
        ProcessoFaseHistoricoEntity::class,
        ObservacaoVersaoEntity::class,
        ItemEntity::class,
        AnexoLinkEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pessoaDao(): PessoaDao
    abstract fun faseDao(): FaseDao
    abstract fun processoDao(): ProcessoDao
    abstract fun processoFaseHistoricoDao(): ProcessoFaseHistoricoDao
    abstract fun observacaoVersaoDao(): ObservacaoVersaoDao
    abstract fun itemDao(): ItemDao
    abstract fun anexoLinkDao(): AnexoLinkDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "organize_processo.db"
                ).build().also { instancia = it }
            }
    }
}
