/*
 *
 *  * MIT License
 *  *
 *  * Copyright (c) 2020 Spikey Sanju
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 *
 */

package www.thecodemonks.techbytes.ui.articles

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import www.thecodemonks.techbytes.R
import www.thecodemonks.techbytes.databinding.FragmentArticlesBinding
import www.thecodemonks.techbytes.model.Category
import www.thecodemonks.techbytes.ui.adapter.CategoryAdapter
import www.thecodemonks.techbytes.ui.adapter.NewsAdapter
import www.thecodemonks.techbytes.ui.base.BaseActivity
import www.thecodemonks.techbytes.ui.viewmodel.ArticleViewModel
import www.thecodemonks.techbytes.utils.Constants.NY_BUSINESS
import www.thecodemonks.techbytes.utils.Constants.NY_EDUCATION
import www.thecodemonks.techbytes.utils.Constants.NY_SCIENCE
import www.thecodemonks.techbytes.utils.Constants.NY_SPACE
import www.thecodemonks.techbytes.utils.Constants.NY_SPORTS
import www.thecodemonks.techbytes.utils.Constants.NY_TECH
import www.thecodemonks.techbytes.utils.Constants.NY_YOURMONEY
import www.thecodemonks.techbytes.utils.NetworkUtils
import www.thecodemonks.techbytes.utils.SpacesItemDecorator
import www.thecodemonks.techbytes.utils.hide
import www.thecodemonks.techbytes.utils.show


class ArticlesFragment : Fragment(R.layout.fragment_articles) {

    private lateinit var viewModel: ArticleViewModel
    private lateinit var newsAdapter: NewsAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var category: MutableList<Category>
    private lateinit var _binding: FragmentArticlesBinding
    private val binding get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentArticlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)

        // init article rv
        setUpArticleRV()

        // init show viewModel
        viewModel = (activity as BaseActivity).viewModel

        initCatergory()
        observeNetworkConnectivity()
        observeTopics()
        observeArticles()
        categoryItemOnClick()
        articleItemOnClick()
        swipeToRefreshArticles()
    }

    private fun initCatergory() {
        // add category list
        category = mutableListOf(
            Category("Business", NY_BUSINESS),
            Category("Education", NY_EDUCATION),
            Category("Science", NY_SCIENCE),
            Category("Space", NY_SPACE),
            Category("Sports", NY_SPORTS),
            Category("Tech", NY_TECH),
            Category("Your money", NY_YOURMONEY)
        )

        // attach category list to adapter
        categoryAdapter = CategoryAdapter(category)
        binding.categoryRv.rootView.post {
            binding.categoryRv.adapter = categoryAdapter
            binding.categoryRv.addItemDecoration(SpacesItemDecorator(16))
        }
    }

    private fun swipeToRefreshArticles() {
        binding.refreshArticles.setOnRefreshListener {
            viewModel.reCrawlFromNYTimes {
                binding.refreshArticles.isRefreshing = true
            }
        }
    }

    private fun observeTopics() {
        // observe changes on topic change for list
        viewModel.currentTopic.observe(viewLifecycleOwner) {
            binding.articleRv.animate().alpha(0f)
                .withStartAction {
                    if (viewModel.networkObserver.value == true) {
                        binding.refreshArticles.isRefreshing = true
                    }
                }
                .withEndAction {
                    viewModel.crawlFromNYTimes(it.toString())
                }
        }
    }

    private fun articleItemOnClick() {
        // pass bundle onclick
        newsAdapter.setOnItemClickListener { article ->
            val bundle = Bundle().apply {
                putSerializable("article", article)
            }
            findNavController().navigate(
                R.id.action_articlesFragment_to_articleDetailsFragment,
                bundle
            )
        }
    }

    private fun categoryItemOnClick() {
        // onclick to select source & post value to liveData
        categoryAdapter.setOnItemClickListener {
            viewModel.currentTopic.value = it.source
        }
    }

    private fun observeArticles() {
        // observe the articles
        viewModel.articles.observe(viewLifecycleOwner) {
            binding.refreshArticles.isRefreshing = false
            newsAdapter.differ.submitList(it)
            binding.articleRv.animate().alpha(1f)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu, menu)

        // Set the item state
        lifecycleScope.launch {
            val isChecked = viewModel.readDataStore.first()
            val item = menu.findItem(R.id.action_night_mode)
            item.isChecked = isChecked
            setUIMode(item, isChecked)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        return when (item.itemId) {
            R.id.action_bookmark -> {
                findNavController().navigate(R.id.action_articlesFragment_to_bookmarksFragment)
                true
            }

            R.id.action_night_mode -> {
                item.isChecked = !item.isChecked
                setUIMode(item, item.isChecked)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setUIMode(item: MenuItem, isChecked: Boolean) {
        if (isChecked) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            viewModel.saveToDataStore(true)
            item.setIcon(R.drawable.ic_night)

        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            viewModel.saveToDataStore(false)
            item.setIcon(R.drawable.ic_day)

        }
    }

    private fun setUpArticleRV() {
        newsAdapter = NewsAdapter()
        binding.articleRv.apply {
            adapter = newsAdapter
            addItemDecoration(SpacesItemDecorator(16))
        }
    }

    private fun observeNetworkConnectivity() {
        NetworkUtils.observeConnectivity(requireContext())
            .observe(viewLifecycleOwner) { isConnected ->
                if (isConnected) onConnectivityAvailable() else onConnectivityUnavailable()
            }
    }

    private fun onConnectivityAvailable() {
        binding.run {
            textNetworkStatus.text = getString(R.string.text_connectivity)
            containerNetworkStatus.apply {
                setBackgroundColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.colorStatusConnected,
                        requireActivity().theme
                    )
                )
                animate()
                    .alpha(1f)
                    .setStartDelay(ANIMATION_DURATION)
                    .setDuration(ANIMATION_DURATION)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            hide()
                        }
                    })
                    .start()
            }
        }
    }

    private fun onConnectivityUnavailable() {
        binding.run {
            textNetworkStatus.text = getString(R.string.text_no_connectivity)
            val offlineDrawable =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_internet_off)
            binding.textNetworkStatus.setCompoundDrawablesWithIntrinsicBounds(
                offlineDrawable,
                null,
                null,
                null
            )
            binding.containerNetworkStatus.apply {
                show()
                setBackgroundColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.colorStatusNotConnected,
                        requireActivity().theme
                    )
                )
            }
        }
    }

    companion object {
        const val ANIMATION_DURATION = 3000L
    }
}