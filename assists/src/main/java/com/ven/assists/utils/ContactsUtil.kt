package com.ven.assists.utils

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.RawContacts
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 联系人信息数据类
 * @param id 联系人ID
 * @param name 联系人姓名
 * @param phoneNumbers 电话号码列表
 * @param emails 邮箱列表
 * @param address 地址
 */
data class ContactInfo(
    val id: Long,
    val name: String,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val address: String? = null
)

/**
 * 通讯录工具类
 * 用于向系统通讯录中添加联系人和读取联系人信息
 */
object ContactsUtil {

    /**
     * 请求读取通讯录权限
     * @return 是否授予权限
     */
    private suspend fun requestReadPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        PermissionUtils.permission(Manifest.permission.READ_CONTACTS)
            .callback(object : PermissionUtils.SimpleCallback {
                override fun onGranted() {
                    continuation.resume(true)
                }

                override fun onDenied() {
                    continuation.resume(false)
                }
            })
            .request()
    }

    /**
     * 请求写入通讯录权限
     * @return 是否授予权限
     */
    private suspend fun requestWritePermission(): Boolean = suspendCancellableCoroutine { continuation ->
        PermissionUtils.permission(Manifest.permission.WRITE_CONTACTS)
            .callback(object : PermissionUtils.SimpleCallback {
                override fun onGranted() {
                    continuation.resume(true)
                }

                override fun onDenied() {
                    continuation.resume(false)
                }
            })
            .request()
    }

    /**
     * 请求读写通讯录权限
     * @return 是否授予权限
     */
    private suspend fun requestReadWritePermission(): Boolean = suspendCancellableCoroutine { continuation ->
        PermissionUtils.permission(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
            .callback(object : PermissionUtils.SimpleCallback {
                override fun onGranted() {
                    continuation.resume(true)
                }

                override fun onDenied() {
                    continuation.resume(false)
                }
            })
            .request()
    }

    /**
     * 检查是否有读取通讯录权限
     * @return 是否有权限
     */
    private fun hasReadPermission(): Boolean {
        return PermissionUtils.isGranted(Manifest.permission.READ_CONTACTS)
    }

    /**
     * 检查是否有写入通讯录权限
     * @return 是否有权限
     */
    private fun hasWritePermission(): Boolean {
        return PermissionUtils.isGranted(Manifest.permission.WRITE_CONTACTS)
    }

    /**
     * 向通讯录中添加联系人
     * @param name 联系人姓名
     * @param phoneNumber 联系人电话号码
     * @return 添加是否成功
     */
    suspend fun addContact(name: String, phoneNumber: String): Boolean {
        // 检查权限，如果没有则请求
        if (!hasWritePermission()) {
            val granted = requestWritePermission()
            if (!granted) return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext false
                val operations = ArrayList<ContentProviderOperation>()

                // 创建一个新的RawContact
                operations.add(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_TYPE, null)
                        .withValue(RawContacts.ACCOUNT_NAME, null)
                        .build()
                )

                // 添加姓名
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                        .build()
                )

                // 添加电话号码
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )

                // 执行批量操作
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 向通讯录中添加联系人（支持多个电话号码）
     * @param name 联系人姓名
     * @param phoneNumbers 联系人电话号码列表
     * @return 添加是否成功
     */
    suspend fun addContactWithMultiplePhones(name: String, phoneNumbers: List<String>): Boolean {
        // 检查权限，如果没有则请求
        if (!hasWritePermission()) {
            val granted = requestWritePermission()
            if (!granted) return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext false
                if (phoneNumbers.isEmpty()) return@withContext false

                val operations = ArrayList<ContentProviderOperation>()

            // 创建一个新的RawContact
            operations.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // 添加姓名
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // 添加多个电话号码
            phoneNumbers.forEachIndexed { index, phoneNumber ->
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(
                            CommonDataKinds.Phone.TYPE,
                            if (index == 0) CommonDataKinds.Phone.TYPE_MOBILE
                            else CommonDataKinds.Phone.TYPE_OTHER
                        )
                        .build()
                )
            }

                // 执行批量操作
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 向通讯录中添加完整联系人信息
     * @param name 联系人姓名
     * @param phoneNumber 联系人电话号码
     * @param email 联系人邮箱（可选）
     * @param address 联系人地址（可选）
     * @return 添加是否成功
     */
    suspend fun addContactWithDetails(
        name: String,
        phoneNumber: String,
        email: String? = null,
        address: String? = null
    ): Boolean {
        // 检查权限，如果没有则请求
        if (!hasWritePermission()) {
            val granted = requestWritePermission()
            if (!granted) return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext false
                val operations = ArrayList<ContentProviderOperation>()

            // 创建一个新的RawContact
            operations.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // 添加姓名
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // 添加电话号码
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            // 添加邮箱（如果提供）
            if (!email.isNullOrEmpty()) {
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(CommonDataKinds.Email.ADDRESS, email)
                        .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            // 添加地址（如果提供）
            if (!address.isNullOrEmpty()) {
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                        )
                        .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address)
                        .withValue(
                            CommonDataKinds.StructuredPostal.TYPE,
                            CommonDataKinds.StructuredPostal.TYPE_HOME
                        )
                        .build()
                )
            }

                // 执行批量操作
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 检查联系人是否已存在
     * @param phoneNumber 电话号码
     * @return 是否存在
     */
    suspend fun isContactExists(phoneNumber: String): Boolean {
        // 检查权限，如果没有则请求
        if (!hasReadPermission()) {
            val granted = requestReadPermission()
            if (!granted) return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext false
                val cursor = context.contentResolver.query(
                    CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(CommonDataKinds.Phone.NUMBER),
                    "${CommonDataKinds.Phone.NUMBER} = ?",
                    arrayOf(phoneNumber),
                    null
                )
                val exists = cursor?.count ?: 0 > 0
                cursor?.close()
                exists
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 根据电话号码删除联系人
     * @param phoneNumber 电话号码
     * @return 删除是否成功
     */
    suspend fun deleteContactByPhone(phoneNumber: String): Boolean {
        // 检查权限，如果没有则请求
        if (!hasWritePermission()) {
            val granted = requestWritePermission()
            if (!granted) return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext false
                
                // 查询联系人ID
                val cursor = context.contentResolver.query(
                    CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(CommonDataKinds.Phone.CONTACT_ID),
                    "${CommonDataKinds.Phone.NUMBER} = ?",
                    arrayOf(phoneNumber),
                    null
                )

                var deleted = false
                cursor?.use {
                    if (it.moveToFirst()) {
                        val contactId = it.getLong(
                            it.getColumnIndexOrThrow(CommonDataKinds.Phone.CONTACT_ID)
                        )
                        
                        // 删除联系人
                        val deletedRows = context.contentResolver.delete(
                            ContactsContract.RawContacts.CONTENT_URI,
                            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                            arrayOf(contactId.toString())
                        )
                        deleted = deletedRows > 0
                    }
                }
                
                deleted
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 获取所有联系人
     * @return 联系人列表
     */
    suspend fun getAllContacts(): List<ContactInfo> {
        // 检查权限，如果没有则请求
        if (!hasReadPermission()) {
            val granted = requestReadPermission()
            if (!granted) return emptyList()
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext emptyList()
                val contactsList = mutableListOf<ContactInfo>()
                val contactsMap = mutableMapOf<Long, ContactInfo>()

            // 查询所有联系人
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getLong(
                        it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    )
                    val name = it.getString(
                        it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    ) ?: ""

                    contactsMap[contactId] = ContactInfo(
                        id = contactId,
                        name = name
                    )
                }
            }

            // 获取每个联系人的电话号码
            contactsMap.keys.forEach { contactId ->
                val phoneNumbers = getPhoneNumbers(contactId)
                val emails = getEmails(contactId)
                val address = getAddress(contactId)

                contactsMap[contactId]?.let { contact ->
                    contactsList.add(
                        contact.copy(
                            phoneNumbers = phoneNumbers,
                            emails = emails,
                            address = address
                        )
                    )
                }
            }

                contactsList
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * 根据姓名搜索联系人
     * @param name 联系人姓名（支持模糊搜索）
     * @return 联系人列表
     */
    suspend fun searchContactsByName(name: String): List<ContactInfo> {
        // 检查权限，如果没有则请求
        if (!hasReadPermission()) {
            val granted = requestReadPermission()
            if (!granted) return emptyList()
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext emptyList()
                val contactsList = mutableListOf<ContactInfo>()
                val contactsMap = mutableMapOf<Long, ContactInfo>()

            // 模糊查询联系人
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getLong(
                        it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    )
                    val displayName = it.getString(
                        it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    ) ?: ""

                    contactsMap[contactId] = ContactInfo(
                        id = contactId,
                        name = displayName
                    )
                }
            }

            // 获取每个联系人的详细信息
            contactsMap.keys.forEach { contactId ->
                val phoneNumbers = getPhoneNumbers(contactId)
                val emails = getEmails(contactId)
                val address = getAddress(contactId)

                contactsMap[contactId]?.let { contact ->
                    contactsList.add(
                        contact.copy(
                            phoneNumbers = phoneNumbers,
                            emails = emails,
                            address = address
                        )
                    )
                }
            }

                contactsList
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * 根据电话号码查询联系人
     * @param phoneNumber 电话号码
     * @return 联系人信息，如果不存在则返回null
     */
    suspend fun getContactByPhone(phoneNumber: String): ContactInfo? {
        // 检查权限，如果没有则请求
        if (!hasReadPermission()) {
            val granted = requestReadPermission()
            if (!granted) return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext null

                // 查询电话号码对应的联系人ID
                val cursor = context.contentResolver.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    CommonDataKinds.Phone.CONTACT_ID,
                    CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${CommonDataKinds.Phone.NUMBER} = ?",
                arrayOf(phoneNumber),
                null
            )

            var contactInfo: ContactInfo? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId = it.getLong(
                        it.getColumnIndexOrThrow(CommonDataKinds.Phone.CONTACT_ID)
                    )
                    val name = it.getString(
                        it.getColumnIndexOrThrow(CommonDataKinds.Phone.DISPLAY_NAME)
                    ) ?: ""

                    // 获取该联系人的所有信息
                    contactInfo = ContactInfo(
                        id = contactId,
                        name = name,
                        phoneNumbers = getPhoneNumbers(contactId),
                        emails = getEmails(contactId),
                        address = getAddress(contactId)
                    )
                }
            }

                contactInfo
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * 获取联系人的详细信息
     * @param contactId 联系人ID
     * @return 联系人信息，如果不存在则返回null
     */
    suspend fun getContactById(contactId: Long): ContactInfo? {
        // 检查权限，如果没有则请求
        if (!hasReadPermission()) {
            val granted = requestReadPermission()
            if (!granted) return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext null

                // 查询联系人基本信息
                val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            var contactInfo: ContactInfo? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(
                        it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    ) ?: ""

                    contactInfo = ContactInfo(
                        id = contactId,
                        name = name,
                        phoneNumbers = getPhoneNumbers(contactId),
                        emails = getEmails(contactId),
                        address = getAddress(contactId)
                    )
                }
            }

                contactInfo
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * 获取指定联系人的所有电话号码
     * @param contactId 联系人ID
     * @return 电话号码列表
     */
    private fun getPhoneNumbers(contactId: Long): List<String> {
        return try {
            val context = ActivityUtils.getTopActivity() ?: return emptyList()
            val phoneNumbers = mutableListOf<String>()

            val cursor = context.contentResolver.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(CommonDataKinds.Phone.NUMBER),
                "${CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val phoneNumber = it.getString(
                        it.getColumnIndexOrThrow(CommonDataKinds.Phone.NUMBER)
                    )
                    if (!phoneNumber.isNullOrEmpty()) {
                        phoneNumbers.add(phoneNumber)
                    }
                }
            }

            phoneNumbers
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取指定联系人的所有邮箱
     * @param contactId 联系人ID
     * @return 邮箱列表
     */
    private fun getEmails(contactId: Long): List<String> {
        return try {
            val context = ActivityUtils.getTopActivity() ?: return emptyList()
            val emails = mutableListOf<String>()

            val cursor = context.contentResolver.query(
                CommonDataKinds.Email.CONTENT_URI,
                arrayOf(CommonDataKinds.Email.ADDRESS),
                "${CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val email = it.getString(
                        it.getColumnIndexOrThrow(CommonDataKinds.Email.ADDRESS)
                    )
                    if (!email.isNullOrEmpty()) {
                        emails.add(email)
                    }
                }
            }

            emails
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取指定联系人的地址
     * @param contactId 联系人ID
     * @return 地址，如果没有则返回null
     */
    private fun getAddress(contactId: Long): String? {
        return try {
            val context = ActivityUtils.getTopActivity() ?: return null

            val cursor = context.contentResolver.query(
                CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
                "${CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            var address: String? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    address = it.getString(
                        it.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                    )
                }
            }

            address
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取通讯录中联系人的总数
     * @return 联系人总数
     */
    suspend fun getContactCount(): Int {
        // 检查权限，如果没有则请求
        if (!hasReadPermission()) {
            val granted = requestReadPermission()
            if (!granted) return 0
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val context = ActivityUtils.getTopActivity() ?: return@withContext 0

                val cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID),
                    null,
                    null,
                    null
                )

                val count = cursor?.count ?: 0
                cursor?.close()
                count
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }
}

