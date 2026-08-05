package com.example.voipapp.worker

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.voipapp.data.Contact

class ContactSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val contacts = fetchContacts()
            Log.d("ContactSyncWorker", "Synced ${contacts.size} contacts")
            // Simulate network upload
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("ContactSyncWorker", "Error syncing contacts", e)
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    private fun fetchContacts(): List<Contact> {
        val contactList = mutableListOf<Contact>()
        val contentResolver = applicationContext.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            if (idIndex != -1 && nameIndex != -1 && numberIndex != -1) {
                while (it.moveToNext()) {
                    val id = it.getString(idIndex) ?: ""
                    val name = it.getString(nameIndex) ?: ""
                    val number = it.getString(numberIndex) ?: ""
                    contactList.add(Contact(id, name, number))
                }
            }
        }
        return contactList
    }
}
