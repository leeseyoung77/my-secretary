package com.luxboy.mysecretary.data.repository

import com.luxboy.mysecretary.data.local.ContactDao
import com.luxboy.mysecretary.data.local.ContactEntity
import com.luxboy.mysecretary.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val dao: ContactDao,
) {
    fun observeAll(): Flow<List<Contact>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun findById(id: Long): Contact? = dao.findById(id)?.toDomain()

    suspend fun upsert(contact: Contact): Long {
        val now = System.currentTimeMillis()
        val entity = ContactEntity(
            id = contact.id,
            name = contact.name.trim(),
            phoneNumber = contact.phoneNumber.trim(),
            createdAt = now,
        )
        return if (contact.id == 0L) dao.insert(entity)
        else { dao.update(entity); contact.id }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun findByName(query: String): List<Contact> {
        val keyword = query.trim()
        if (keyword.isBlank()) return emptyList()
        val all = dao.getAll().map { it.toDomain() }
        val exact = all.filter { it.name.equals(keyword, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return all.filter { it.name.contains(keyword, ignoreCase = true) }
    }

    private fun ContactEntity.toDomain(): Contact = Contact(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
    )
}
