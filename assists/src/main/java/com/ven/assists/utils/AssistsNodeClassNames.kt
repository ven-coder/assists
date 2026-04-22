package com.ven.assists.utils

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍节点 [AccessibilityNodeInfo.getClassName] 常见取值（与系统 / AndroidX / Material 实际类名一致）。
 * 用于与节点 className 比对，不引入对应 UI 库依赖。
 */
object AssistsNodeClassNames {
    // region android.view
    const val View = "android.view.View"
    const val ViewGroup = "android.view.ViewGroup"
    const val TextureView = "android.view.TextureView"
    const val SurfaceView = "android.view.SurfaceView"
    // endregion

    // region android.widget — 布局容器
    const val FrameLayout = "android.widget.FrameLayout"
    const val LinearLayout = "android.widget.LinearLayout"
    const val RelativeLayout = "android.widget.RelativeLayout"
    const val AbsoluteLayout = "android.widget.AbsoluteLayout"
    const val GridLayout = "android.widget.GridLayout"
    const val TableLayout = "android.widget.TableLayout"
    const val TableRow = "android.widget.TableRow"
    const val ScrollView = "android.widget.ScrollView"
    const val HorizontalScrollView = "android.widget.HorizontalScrollView"
    const val Space = "android.widget.Space"
    // endregion

    // region android.widget — 列表与适配器视图
    const val AdapterView = "android.widget.AdapterView"
    const val ListView = "android.widget.ListView"
    const val GridView = "android.widget.GridView"
    const val ExpandableListView = "android.widget.ExpandableListView"
    const val Spinner = "android.widget.Spinner"
    // endregion

    // region android.widget — 切换 / 动画容器
    const val ViewFlipper = "android.widget.ViewFlipper"
    const val ViewSwitcher = "android.widget.ViewSwitcher"
    const val ViewAnimator = "android.widget.ViewAnimator"
    const val StackView = "android.widget.StackView"
    const val TextSwitcher = "android.widget.TextSwitcher"
    const val ImageSwitcher = "android.widget.ImageSwitcher"
    // endregion

    // region android.widget — 文本与基础控件
    const val TextView = "android.widget.TextView"
    const val Button = "android.widget.Button"
    const val ImageButton = "android.widget.ImageButton"
    const val ImageView = "android.widget.ImageView"
    const val EditText = "android.widget.EditText"
    const val AutoCompleteTextView = "android.widget.AutoCompleteTextView"
    const val MultiAutoCompleteTextView = "android.widget.MultiAutoCompleteTextView"
    const val SearchView = "android.widget.SearchView"
    const val CheckedTextView = "android.widget.CheckedTextView"
    // endregion

    // region android.widget — 复合按钮与选择
    const val CompoundButton = "android.widget.CompoundButton"
    const val CheckBox = "android.widget.CheckBox"
    const val RadioButton = "android.widget.RadioButton"
    const val RadioGroup = "android.widget.RadioGroup"
    const val Switch = "android.widget.Switch"
    const val ToggleButton = "android.widget.ToggleButton"
    // endregion

    // region android.widget — 进度与评分
    const val SeekBar = "android.widget.SeekBar"
    const val ProgressBar = "android.widget.ProgressBar"
    const val RatingBar = "android.widget.RatingBar"
    // endregion

    // region android.widget — 其它常用控件
    const val Chronometer = "android.widget.Chronometer"
    const val CalendarView = "android.widget.CalendarView"
    const val DatePicker = "android.widget.DatePicker"
    const val TimePicker = "android.widget.TimePicker"
    const val NumberPicker = "android.widget.NumberPicker"
    const val TabHost = "android.widget.TabHost"
    const val Toolbar = "android.widget.Toolbar"
    // endregion

    // region androidx（常见 className，无需在工程中依赖对应 artifact 亦可比对）
    const val RecyclerView = "androidx.recyclerview.widget.RecyclerView"
    const val ViewPager = "androidx.viewpager.widget.ViewPager"
    const val ViewPager2 = "androidx.viewpager2.widget.ViewPager2"
    const val ConstraintLayout = "androidx.constraintlayout.widget.ConstraintLayout"
    const val CoordinatorLayout = "androidx.coordinatorlayout.widget.CoordinatorLayout"
    const val DrawerLayout = "androidx.drawerlayout.widget.DrawerLayout"
    const val SwipeRefreshLayout = "androidx.swiperefreshlayout.widget.SwipeRefreshLayout"
    const val NestedScrollView = "androidx.core.widget.NestedScrollView"
    const val AppCompatToolbar = "androidx.appcompat.widget.Toolbar"
    const val LinearLayoutCompat = "androidx.appcompat.widget.LinearLayoutCompat"
    const val GridLayoutCompat = "androidx.gridlayout.widget.GridLayout"
    const val CardView = "androidx.cardview.widget.CardView"
    // endregion

    // region Material Components（字符串与运行时类名一致即可）
    const val MaterialButton = "com.google.android.material.button.MaterialButton"
    const val TextInputLayout = "com.google.android.material.textfield.TextInputLayout"
    const val TextInputEditText = "com.google.android.material.textfield.TextInputEditText"
    const val FloatingActionButton = "com.google.android.material.floatingactionbutton.FloatingActionButton"
    const val TabLayout = "com.google.android.material.tabs.TabLayout"
    const val BottomNavigationView = "com.google.android.material.bottomnavigation.BottomNavigationView"
    const val MaterialCardView = "com.google.android.material.card.MaterialCardView"
    const val AppBarLayout = "com.google.android.material.appbar.AppBarLayout"
    const val CollapsingToolbarLayout = "com.google.android.material.appbar.CollapsingToolbarLayout"
    const val NavigationView = "com.google.android.material.navigation.NavigationView"
    const val Chip = "com.google.android.material.chip.Chip"
    const val ChipGroup = "com.google.android.material.chip.ChipGroup"
    // endregion

    // region android.webkit
    const val WebView = "android.webkit.WebView"
    // endregion
}

private fun AccessibilityNodeInfo.classEquals(expected: String): Boolean = className == expected

// region android.view
fun AccessibilityNodeInfo.isView(): Boolean = classEquals(AssistsNodeClassNames.View)
fun AccessibilityNodeInfo.isViewGroup(): Boolean = classEquals(AssistsNodeClassNames.ViewGroup)
fun AccessibilityNodeInfo.isTextureView(): Boolean = classEquals(AssistsNodeClassNames.TextureView)
fun AccessibilityNodeInfo.isSurfaceView(): Boolean = classEquals(AssistsNodeClassNames.SurfaceView)
// endregion

// region android.widget — 布局
fun AccessibilityNodeInfo.isFrameLayout(): Boolean = classEquals(AssistsNodeClassNames.FrameLayout)
fun AccessibilityNodeInfo.isLinearLayout(): Boolean = classEquals(AssistsNodeClassNames.LinearLayout)
fun AccessibilityNodeInfo.isRelativeLayout(): Boolean = classEquals(AssistsNodeClassNames.RelativeLayout)
fun AccessibilityNodeInfo.isAbsoluteLayout(): Boolean = classEquals(AssistsNodeClassNames.AbsoluteLayout)
/** 对应 [android.widget.GridLayout] */
fun AccessibilityNodeInfo.isAndroidGridLayout(): Boolean = classEquals(AssistsNodeClassNames.GridLayout)
fun AccessibilityNodeInfo.isTableLayout(): Boolean = classEquals(AssistsNodeClassNames.TableLayout)
fun AccessibilityNodeInfo.isTableRow(): Boolean = classEquals(AssistsNodeClassNames.TableRow)
fun AccessibilityNodeInfo.isScrollView(): Boolean = classEquals(AssistsNodeClassNames.ScrollView)
fun AccessibilityNodeInfo.isHorizontalScrollView(): Boolean = classEquals(AssistsNodeClassNames.HorizontalScrollView)
fun AccessibilityNodeInfo.isSpace(): Boolean = classEquals(AssistsNodeClassNames.Space)
// endregion

// region 列表与适配器
fun AccessibilityNodeInfo.isAdapterView(): Boolean = classEquals(AssistsNodeClassNames.AdapterView)
fun AccessibilityNodeInfo.isListView(): Boolean = classEquals(AssistsNodeClassNames.ListView)
fun AccessibilityNodeInfo.isGridView(): Boolean = classEquals(AssistsNodeClassNames.GridView)
fun AccessibilityNodeInfo.isExpandableListView(): Boolean = classEquals(AssistsNodeClassNames.ExpandableListView)
fun AccessibilityNodeInfo.isSpinner(): Boolean = classEquals(AssistsNodeClassNames.Spinner)
// endregion

// region 切换容器
fun AccessibilityNodeInfo.isViewFlipper(): Boolean = classEquals(AssistsNodeClassNames.ViewFlipper)
fun AccessibilityNodeInfo.isViewSwitcher(): Boolean = classEquals(AssistsNodeClassNames.ViewSwitcher)
fun AccessibilityNodeInfo.isViewAnimator(): Boolean = classEquals(AssistsNodeClassNames.ViewAnimator)
fun AccessibilityNodeInfo.isStackView(): Boolean = classEquals(AssistsNodeClassNames.StackView)
fun AccessibilityNodeInfo.isTextSwitcher(): Boolean = classEquals(AssistsNodeClassNames.TextSwitcher)
fun AccessibilityNodeInfo.isImageSwitcher(): Boolean = classEquals(AssistsNodeClassNames.ImageSwitcher)
// endregion

// region 文本与输入
fun AccessibilityNodeInfo.isTextView(): Boolean = classEquals(AssistsNodeClassNames.TextView)
fun AccessibilityNodeInfo.isButton(): Boolean = classEquals(AssistsNodeClassNames.Button)
fun AccessibilityNodeInfo.isImageButton(): Boolean = classEquals(AssistsNodeClassNames.ImageButton)
fun AccessibilityNodeInfo.isImageView(): Boolean = classEquals(AssistsNodeClassNames.ImageView)
fun AccessibilityNodeInfo.isEditText(): Boolean = classEquals(AssistsNodeClassNames.EditText)
fun AccessibilityNodeInfo.isAutoCompleteTextView(): Boolean = classEquals(AssistsNodeClassNames.AutoCompleteTextView)
fun AccessibilityNodeInfo.isMultiAutoCompleteTextView(): Boolean = classEquals(AssistsNodeClassNames.MultiAutoCompleteTextView)
fun AccessibilityNodeInfo.isSearchView(): Boolean = classEquals(AssistsNodeClassNames.SearchView)
fun AccessibilityNodeInfo.isCheckedTextView(): Boolean = classEquals(AssistsNodeClassNames.CheckedTextView)
// endregion

// region 复合按钮
fun AccessibilityNodeInfo.isCompoundButton(): Boolean = classEquals(AssistsNodeClassNames.CompoundButton)
fun AccessibilityNodeInfo.isCheckBox(): Boolean = classEquals(AssistsNodeClassNames.CheckBox)
fun AccessibilityNodeInfo.isRadioButton(): Boolean = classEquals(AssistsNodeClassNames.RadioButton)
fun AccessibilityNodeInfo.isRadioGroup(): Boolean = classEquals(AssistsNodeClassNames.RadioGroup)
fun AccessibilityNodeInfo.isSwitch(): Boolean = classEquals(AssistsNodeClassNames.Switch)
fun AccessibilityNodeInfo.isToggleButton(): Boolean = classEquals(AssistsNodeClassNames.ToggleButton)
// endregion

// region 进度
fun AccessibilityNodeInfo.isSeekBar(): Boolean = classEquals(AssistsNodeClassNames.SeekBar)
fun AccessibilityNodeInfo.isProgressBar(): Boolean = classEquals(AssistsNodeClassNames.ProgressBar)
fun AccessibilityNodeInfo.isRatingBar(): Boolean = classEquals(AssistsNodeClassNames.RatingBar)
// endregion

// region 其它 widget
fun AccessibilityNodeInfo.isChronometer(): Boolean = classEquals(AssistsNodeClassNames.Chronometer)
fun AccessibilityNodeInfo.isCalendarView(): Boolean = classEquals(AssistsNodeClassNames.CalendarView)
fun AccessibilityNodeInfo.isDatePicker(): Boolean = classEquals(AssistsNodeClassNames.DatePicker)
fun AccessibilityNodeInfo.isTimePicker(): Boolean = classEquals(AssistsNodeClassNames.TimePicker)
fun AccessibilityNodeInfo.isNumberPicker(): Boolean = classEquals(AssistsNodeClassNames.NumberPicker)
fun AccessibilityNodeInfo.isTabHost(): Boolean = classEquals(AssistsNodeClassNames.TabHost)
fun AccessibilityNodeInfo.isToolbar(): Boolean = classEquals(AssistsNodeClassNames.Toolbar)
// endregion

// region androidx
fun AccessibilityNodeInfo.isRecyclerView(): Boolean = classEquals(AssistsNodeClassNames.RecyclerView)
fun AccessibilityNodeInfo.isViewPager(): Boolean = classEquals(AssistsNodeClassNames.ViewPager)
fun AccessibilityNodeInfo.isViewPager2(): Boolean = classEquals(AssistsNodeClassNames.ViewPager2)
fun AccessibilityNodeInfo.isConstraintLayout(): Boolean = classEquals(AssistsNodeClassNames.ConstraintLayout)
fun AccessibilityNodeInfo.isCoordinatorLayout(): Boolean = classEquals(AssistsNodeClassNames.CoordinatorLayout)
fun AccessibilityNodeInfo.isDrawerLayout(): Boolean = classEquals(AssistsNodeClassNames.DrawerLayout)
fun AccessibilityNodeInfo.isSwipeRefreshLayout(): Boolean = classEquals(AssistsNodeClassNames.SwipeRefreshLayout)
fun AccessibilityNodeInfo.isNestedScrollView(): Boolean = classEquals(AssistsNodeClassNames.NestedScrollView)
fun AccessibilityNodeInfo.isAppCompatToolbar(): Boolean = classEquals(AssistsNodeClassNames.AppCompatToolbar)
fun AccessibilityNodeInfo.isLinearLayoutCompat(): Boolean = classEquals(AssistsNodeClassNames.LinearLayoutCompat)
fun AccessibilityNodeInfo.isGridLayoutCompat(): Boolean = classEquals(AssistsNodeClassNames.GridLayoutCompat)
fun AccessibilityNodeInfo.isCardView(): Boolean = classEquals(AssistsNodeClassNames.CardView)
// endregion

// region Material
fun AccessibilityNodeInfo.isMaterialButton(): Boolean = classEquals(AssistsNodeClassNames.MaterialButton)
fun AccessibilityNodeInfo.isTextInputLayout(): Boolean = classEquals(AssistsNodeClassNames.TextInputLayout)
fun AccessibilityNodeInfo.isTextInputEditText(): Boolean = classEquals(AssistsNodeClassNames.TextInputEditText)
fun AccessibilityNodeInfo.isFloatingActionButton(): Boolean = classEquals(AssistsNodeClassNames.FloatingActionButton)
fun AccessibilityNodeInfo.isTabLayout(): Boolean = classEquals(AssistsNodeClassNames.TabLayout)
fun AccessibilityNodeInfo.isBottomNavigationView(): Boolean = classEquals(AssistsNodeClassNames.BottomNavigationView)
fun AccessibilityNodeInfo.isMaterialCardView(): Boolean = classEquals(AssistsNodeClassNames.MaterialCardView)
fun AccessibilityNodeInfo.isAppBarLayout(): Boolean = classEquals(AssistsNodeClassNames.AppBarLayout)
fun AccessibilityNodeInfo.isCollapsingToolbarLayout(): Boolean = classEquals(AssistsNodeClassNames.CollapsingToolbarLayout)
fun AccessibilityNodeInfo.isNavigationView(): Boolean = classEquals(AssistsNodeClassNames.NavigationView)
fun AccessibilityNodeInfo.isChip(): Boolean = classEquals(AssistsNodeClassNames.Chip)
fun AccessibilityNodeInfo.isChipGroup(): Boolean = classEquals(AssistsNodeClassNames.ChipGroup)
// endregion

fun AccessibilityNodeInfo.isWebView(): Boolean = classEquals(AssistsNodeClassNames.WebView)
